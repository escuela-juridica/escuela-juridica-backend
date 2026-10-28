package pe.edu.utp.escuela.app.service;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AccesoPeticion;
import pe.edu.utp.escuela.app.dto.ResultadoAcceso;
import pe.edu.utp.escuela.app.dto.SesionAccesoRespuesta;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.InactiveAccountException;
import pe.edu.utp.escuela.app.exception.InvalidCredentialsException;
import pe.edu.utp.escuela.app.exception.PendingEmailVerificationException;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepository;
import pe.edu.utp.escuela.app.security.JwtService;

@Service
@RequiredArgsConstructor
public class AccesoService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public ResultadoAcceso acceder(AccesoPeticion peticion) {
        String correo = peticion.correo().strip().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(InvalidCredentialsException::new);

        if (!usuario.isActivo()) {
            throw new InactiveAccountException();
        }
        if (usuario.getContrasenaHash() == null
                || !passwordEncoder.matches(peticion.contrasena(), usuario.getContrasenaHash())) {
            throw new InvalidCredentialsException();
        }
        if (usuario.getCorreoVerificadoEn() == null) {
            throw new PendingEmailVerificationException();
        }

        String rol = rolPrincipal(usuario.getId());
        String jwt = jwtService.issue(usuario.getId(), usuario.getCorreo(), List.of(rol));
        return new ResultadoAcceso(jwt, construirSesion(usuario, rol));
    }

    @Transactional(readOnly = true)
    public SesionAccesoRespuesta obtenerSesion(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(InvalidCredentialsException::new);
        return construirSesion(usuario, rolPrincipal(usuario.getId()));
    }

    private String rolPrincipal(Long usuarioId) {
        List<String> codigos = usuarioRolRepository.buscarCodigosRolesPrincipales(usuarioId);
        if (codigos.size() != 1) {
            throw new IllegalStateException("La cuenta no tiene un rol principal válido");
        }
        return codigos.get(0).replaceFirst("^ROLE_", "");
    }

    private SesionAccesoRespuesta construirSesion(Usuario usuario, String rol) {
        return new SesionAccesoRespuesta(
                usuario.getId(), usuario.getPersona().nombreCompleto(), usuario.getCorreo(), rol,
                usuario.isRequiereCambioContrasena());
    }
}
