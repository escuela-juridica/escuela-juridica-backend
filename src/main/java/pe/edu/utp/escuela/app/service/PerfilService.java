package pe.edu.utp.escuela.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ActualizarPerfilRequest;
import pe.edu.utp.escuela.app.dto.CambiarContrasenaRequest;
import pe.edu.utp.escuela.app.dto.NuevaContrasenaRequest;
import pe.edu.utp.escuela.app.dto.PerfilResponse;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.InvalidCurrentPasswordException;
import pe.edu.utp.escuela.app.exception.OperationNotAllowedException;
import pe.edu.utp.escuela.app.exception.UnauthorizedException;
import pe.edu.utp.escuela.app.repository.PersonaRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final PersonaRepository personaRepository;
    private final CurrentUserService currentUserService;
    private final TextNormalizer textNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;

    @Transactional(readOnly = true)
    public PerfilResponse obtener() {
        Usuario usuario = usuarioActual();
        return mapear(usuario, usuario.getPersona());
    }

    @Transactional
    public PerfilResponse actualizar(ActualizarPerfilRequest entrada) {
        Usuario usuario = usuarioActual();
        Persona persona = usuario.getPersona();

        String documento = textNormalizer.trimToNull(entrada.documentoIdentidad());
        if (documento != null && !documento.equals(persona.getDocumentoIdentidad())
                && personaRepository.existsByDocumentoIdentidadAndIdNot(documento, persona.getId())) {
            throw new DuplicateResourceException("El documento ya se encuentra registrado.");
        }

        persona.setNombres(textNormalizer.requireText(entrada.nombres(), "Nombres"));
        persona.setApellidoPaterno(textNormalizer.requireText(entrada.apellidoPaterno(), "Apellido paterno"));
        persona.setApellidoMaterno(textNormalizer.trimToNull(entrada.apellidoMaterno()));
        persona.setTelefono(textNormalizer.trimToNull(entrada.telefono()));
        persona.setDocumentoIdentidad(documento);

        return mapear(usuario, persona);
    }

    @Transactional
    public void crearContrasena(NuevaContrasenaRequest entrada) {
        Usuario usuario = usuarioActual();
        if (usuario.getGoogleSubject() == null || usuario.getContrasenaHash() != null) {
            throw new OperationNotAllowedException(
                    "La cuenta no puede crear una contraseña por este medio");
        }
        if (!entrada.contrasena().equals(entrada.confirmacion())) {
            throw new BusinessValidationException("Las contraseñas no coinciden");
        }
        passwordPolicyService.validate(entrada.contrasena());
        usuario.setContrasenaHash(passwordEncoder.encode(entrada.contrasena()));
    }

    /** Para cuentas que ya tienen contraseña propia (con o sin vínculo Google): exige la actual
     * para confirmar identidad antes de reemplazarla. No es el mismo recorrido que HU-004
     * (recuperación por correo, para cuando no la recuerdas). */
    @Transactional
    public void cambiarContrasena(CambiarContrasenaRequest entrada) {
        Usuario usuario = usuarioActual();
        if (usuario.getContrasenaHash() == null) {
            throw new OperationNotAllowedException(
                    "La cuenta todavía no tiene una contraseña propia que cambiar");
        }
        if (!passwordEncoder.matches(entrada.contrasenaActual(), usuario.getContrasenaHash())) {
            throw new InvalidCurrentPasswordException();
        }
        if (!entrada.contrasenaNueva().equals(entrada.confirmacion())) {
            throw new BusinessValidationException("Las contraseñas no coinciden");
        }
        passwordPolicyService.validate(entrada.contrasenaNueva());
        usuario.setContrasenaHash(passwordEncoder.encode(entrada.contrasenaNueva()));
    }

    private Usuario usuarioActual() {
        Long usuarioId = currentUserService.get().userId();
        return usuarioRepository.findByIdAndActivoTrue(usuarioId)
                .orElseThrow(UnauthorizedException::new);
    }

    private PerfilResponse mapear(Usuario usuario, Persona persona) {
        boolean accesoGoogle = usuario.getGoogleSubject() != null;
        return new PerfilResponse(
                persona.getNombres(),
                persona.getApellidoPaterno(),
                persona.getApellidoMaterno(),
                usuario.getCorreo(),
                persona.getTelefono(),
                persona.getDocumentoIdentidad(),
                persona.getFotoUrl(),
                accesoGoogle,
                accesoGoogle && usuario.getContrasenaHash() == null);
    }
}
