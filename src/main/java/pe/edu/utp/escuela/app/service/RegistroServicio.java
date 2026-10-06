package pe.edu.utp.escuela.app.service;

import jakarta.validation.Validator;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.exception.*;
import pe.edu.utp.escuela.app.repository.*;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@Service @RequiredArgsConstructor
public class RegistroServicio {
    private final RegistroPersistencia persistencia;
    private final PasswordPolicyService passwords;
    private final IntegracionesRegistro integraciones;
    private final UsuarioRepositorio usuarios;
    private final PersonaRepositorio personas;
    private final TextNormalizer textos;
    private final Validator validator;

    public RegistroRespuesta registrar(RegistroPeticion p) {
        validar(p);
        if (!p.contrasena().equals(p.confirmarContrasena())) {
            throw new CampoRegistroException(HttpStatus.BAD_REQUEST, "CONTRASENAS_DIFERENTES",
                    "confirmarContrasena", "Las contraseñas no coinciden.");
        }
        passwords.validate(p.contrasena());
        integraciones.verificarAntiRobot(p.evidenciaAntiRobot());
        return conConflictos(p.correo(), p.documentoIdentidad(), () -> persistencia.formulario(p));
    }
    public ContextoRegistroGoogle contextoGoogle(String referencia) {
        var identidad = integraciones.identidadGoogle(referencia);
        comprobarGoogleDisponible(identidad);
        return new ContextoRegistroGoogle(identidad.correo(), identidad.nombres(),
                identidad.apellidos(), identidad.fotoUrl(), identidad.venceEn());
    }
    public SesionRegistroServicio.SesionCreada completarGoogle(RegistroGooglePeticion p) {
        validar(p);
        var identidad = integraciones.identidadGoogle(p.referencia());
        return conConflictos(identidad.correo(), p.documentoIdentidad(),
                () -> persistencia.google(p, identidad));
    }
    private void comprobarGoogleDisponible(ContextoGoogleRegistro.Identidad identidad) {
        if (usuarios.existsByCorreoIgnoreCase(textos.normalizeEmail(identidad.correo()))
                || usuarios.findByGoogleSubject(identidad.subject()).isPresent()) {
            throw new CampoRegistroException(HttpStatus.CONFLICT, "CORREO_DUPLICADO", "correo",
                    "Ya existe una cuenta. Inicia sesión o recupera tu acceso.");
        }
    }
    private <T> T conConflictos(String correo, String documento, Supplier<T> operacion) {
        try { return operacion.get(); }
        catch (DataIntegrityViolationException ex) {
            // Se consulta tras finalizar/revertir la transacción fallida.
            if (usuarios.existsByCorreoIgnoreCase(textos.normalizeEmail(correo))) {
                throw new CampoRegistroException(HttpStatus.CONFLICT, "CORREO_DUPLICADO", "correo",
                        "El correo ya se encuentra registrado.");
            }
            String doc = textos.trimToNull(documento);
            if (doc != null && personas.existsByDocumentoIdentidad(doc)) {
                throw new CampoRegistroException(HttpStatus.CONFLICT, "DOCUMENTO_DUPLICADO",
                        "documentoIdentidad", "El documento ya se encuentra registrado.");
            }
            throw ex;
        }
    }
    private <T> void validar(T peticion) {
        var errores = validator.validate(peticion);
        if (!errores.isEmpty()) {
            var error = errores.iterator().next();
            throw new CampoRegistroException(HttpStatus.BAD_REQUEST, "INVALID_DATA",
                    error.getPropertyPath().toString(), error.getMessage());
        }
    }
}
