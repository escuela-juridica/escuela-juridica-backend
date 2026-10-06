package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.entity.*;
import pe.edu.utp.escuela.app.exception.*;
import pe.edu.utp.escuela.app.repository.*;
import pe.edu.utp.escuela.app.service.ContextoGoogleRegistro;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** Límite transaccional separado para capturar colisiones después del rollback. */
@Service @RequiredArgsConstructor
public class RegistroPersistencia {
    private final PersonaRepositorio personas;
    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final UsuarioRolRepositorio asignaciones;
    private final PasswordEncoder encoder;
    private final CodigoVerificacionServicio codigos;
    private final ReferenciaVerificacionServicio referencias;
    private final SesionRegistroServicio sesiones;
    private final TextNormalizer textos;
    private final Clock clock;

    @Transactional
    public RegistroRespuesta formulario(RegistroPeticion p) {
        String correo = textos.normalizeEmail(p.correo());
        comprobarCorreo(correo);
        Rol rol = rolAlumno();
        String hash;
        try {
            hash = encoder.encode(p.contrasena());
        } catch (IllegalArgumentException ex) {
            // Conserva el codificador compartido; no trunca ni cambia contraseñas.
            throw new CampoRegistroException(HttpStatus.BAD_REQUEST, "CONTRASENA_NO_PROCESABLE",
                    "contrasena", "La contraseña no puede procesarse con el codificador configurado.");
        }
        Persona persona = persona(p.nombres(), p.apellidoPaterno(), p.apellidoMaterno(),
                p.telefono(), p.documentoIdentidad(), null);
        Usuario usuario = usuario(persona, correo, "FORMULARIO");
        usuario.setContrasenaHash(hash);
        usuarios.saveAndFlush(usuario);
        asignarRol(usuario, rol);
        boolean enviado = codigos.emitirPara(usuario);
        return new RegistroRespuesta(usuario.getId(), correo, enviado,
                referencias.emitir(usuario.getId()));
    }

    @Transactional
    public SesionRegistroServicio.SesionCreada google(RegistroGooglePeticion p, ContextoGoogleRegistro.Identidad identidad) {
        String correo = textos.normalizeEmail(identidad.correo());
        comprobarCorreo(correo);
        if (usuarios.findByGoogleSubject(identidad.subject()).isPresent()) {
            throw new CampoRegistroException(HttpStatus.CONFLICT, "CORREO_DUPLICADO", "correo",
                    "Esta identidad de Google ya tiene una cuenta. Inicia sesión.");
        }
        Rol rol = rolAlumno();
        Persona persona = persona(p.nombres(), p.apellidoPaterno(), p.apellidoMaterno(),
                p.telefono(), p.documentoIdentidad(), identidad.fotoUrl());
        Usuario usuario = usuario(persona, correo, "GOOGLE");
        usuario.setGoogleSubject(identidad.subject());
        usuario.setCorreoVerificadoEn(clock.instant());
        usuarios.saveAndFlush(usuario);
        asignarRol(usuario, rol);
        return sesiones.crear(usuario);
    }

    private Persona persona(String nombres, String paterno, String materno, String telefono,
                            String documento, String foto) {
        String doc = textos.trimToNull(documento);
        if (doc != null && personas.existsByDocumentoIdentidad(doc)) {
            throw new CampoRegistroException(HttpStatus.CONFLICT, "DOCUMENTO_DUPLICADO",
                    "documentoIdentidad", "El documento ya se encuentra registrado.");
        }
        Persona persona = new Persona();
        persona.setNombres(textos.requireText(nombres, "Nombres"));
        persona.setApellidoPaterno(textos.requireText(paterno, "Apellidos"));
        persona.setApellidoMaterno(textos.trimToNull(materno));
        persona.setTelefono(textos.trimToNull(telefono));
        persona.setDocumentoIdentidad(doc);
        persona.setFotoUrl(textos.trimToNull(foto));
        return personas.saveAndFlush(persona);
    }
    private Usuario usuario(Persona persona, String correo, String origen) {
        Usuario usuario = new Usuario();
        usuario.setPersona(persona);
        usuario.setCorreo(correo);
        usuario.setOrigenRegistro(origen);
        return usuario;
    }
    private void comprobarCorreo(String correo) {
        if (usuarios.existsByCorreoIgnoreCase(correo)) {
            throw new CampoRegistroException(HttpStatus.CONFLICT, "CORREO_DUPLICADO", "correo",
                    "El correo ya se encuentra registrado. Inicia sesión o recupera tu acceso.");
        }
    }
    private Rol rolAlumno() {
        return roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")
                .orElseThrow(() -> new IllegalStateException("Falta ROLE_ALUMNO activo"));
    }
    private void asignarRol(Usuario usuario, Rol rol) {
        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setId(new UsuarioRol.Clave(usuario.getId(), rol.getId()));
        asignacion.setAsignadoEn(clock.instant());
        asignaciones.saveAndFlush(asignacion);
    }
}
