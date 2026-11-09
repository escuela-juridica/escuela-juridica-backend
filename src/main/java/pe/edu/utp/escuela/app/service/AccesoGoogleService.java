package pe.edu.utp.escuela.app.service;

import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AccesoGoogleResponse;
import pe.edu.utp.escuela.app.dto.RegistroGoogleRequest;
import pe.edu.utp.escuela.app.dto.ResultadoAcceso;
import pe.edu.utp.escuela.app.dto.ResultadoAccesoGoogle;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessException;
import pe.edu.utp.escuela.app.exception.InactiveAccountException;
import pe.edu.utp.escuela.app.exception.InvalidCredentialsException;
import pe.edu.utp.escuela.app.exception.PendingEmailVerificationException;
import pe.edu.utp.escuela.app.integration.ContextoGoogleRegistroIntegration.Identidad;
import pe.edu.utp.escuela.app.integration.GoogleCredentialVerifierIntegration;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class AccesoGoogleService {

    private final ObjectProvider<GoogleCredentialVerifierIntegration> verificador;
    private final UsuarioRepository usuarios;
    private final AccesoService acceso;
    private final ReferenciaGoogleService referencias;
    private final RegistroPersistencia persistencia;

    @Transactional
    public ResultadoAccesoGoogle acceder(String credencial) {
        GoogleCredentialVerifierIntegration integracion = verificador.getIfAvailable();
        if (integracion == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_PENDIENTE",
                    "El acceso con Google todavía no está disponible.");
        }
        Identidad identidad = integracion.verificar(credencial);

        Usuario usuario;
        Optional<Usuario> vinculado = usuarios.findByGoogleSubject(identidad.subject());
        if (vinculado.isPresent()) {
            usuario = vinculado.get();
        } else {
            String correo = identidad.correo().strip().toLowerCase(Locale.ROOT);
            Optional<Usuario> porCorreo = usuarios.findByCorreoIgnoreCase(correo);
            if (porCorreo.isEmpty()) {
                Optional<Usuario> creado = crearCuenta(identidad);
                if (creado.isEmpty()) {
                    return new ResultadoAccesoGoogle(null,
                            AccesoGoogleResponse.paraRegistro(referencias.emitir(identidad)));
                }
                ResultadoAcceso resultado = acceso.abrirSesion(creado.get());
                return new ResultadoAccesoGoogle(resultado.jwt(), AccesoGoogleResponse.conSesion(resultado.sesion()));
            }
            usuario = porCorreo.get();
            if (usuario.getGoogleSubject() != null) {
                throw new InvalidCredentialsException();
            }
        }

        if (!usuario.isActivo()) {
            throw new InactiveAccountException();
        }
        if (usuario.getCorreoVerificadoEn() == null) {
            throw new PendingEmailVerificationException();
        }
        if (usuario.getGoogleSubject() == null) {
            usuario.setGoogleSubject(identidad.subject());
            usuarios.save(usuario);
        }

        ResultadoAcceso resultado = acceso.abrirSesion(usuario);
        return new ResultadoAccesoGoogle(resultado.jwt(), AccesoGoogleResponse.conSesion(resultado.sesion()));
    }

    private Optional<Usuario> crearCuenta(Identidad identidad) {
        String nombres = enMayusculas(identidad.nombres());
        String apellidos = enMayusculas(identidad.apellidos());
        if (nombres.isEmpty() || apellidos.isEmpty()) {
            return Optional.empty();
        }
        RegistroGoogleRequest datos = new RegistroGoogleRequest(null, nombres, apellidos, null, null, null, true);
        try {
            persistencia.google(datos, identidad);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "CORREO_DUPLICADO",
                    "Ya existe una cuenta con este correo. Inténtalo nuevamente.");
        }
        return usuarios.findByGoogleSubject(identidad.subject());
    }

    private String enMayusculas(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.strip().replaceAll("\\s+", " ").toUpperCase(Locale.of("es", "PE"));
    }
}
