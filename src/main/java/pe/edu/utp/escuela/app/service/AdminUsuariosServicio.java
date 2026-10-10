package pe.edu.utp.escuela.app.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CondicionCuentaAdmin;
import pe.edu.utp.escuela.app.dto.RolUsuarioAdmin;
import pe.edu.utp.escuela.app.dto.UsuarioAdminRespuesta;
import pe.edu.utp.escuela.app.dto.UsuarioRolFila;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.UsuarioRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-008 — Gestionar usuarios administrativamente.
 *
 * <p>Esta historia se reasignó como ejercicio técnico del equipo: el controlador y la mayoría de
 * las reglas de esta clase se retiraron a propósito (ver
 * {@code docs/epica-4/HU-008-MAPA-TECNICO-GESTIONAR-USUARIOS.md}, que trae el código íntegro para
 * reconstruirlas).
 *
 * <p><b>NO borrar esta clase completa ni el método {@link #obtener(Long)}</b>: HU-019
 * ({@code MatriculaServicio.tieneRol(...)}, usado por {@code matricularAdministrativamente}) sigue
 * dependiendo de él para validar que el alumno elegido tenga el rol ALUMNO. Revisar ese llamador
 * antes de volver a tocar este archivo.
 */
@Service
@RequiredArgsConstructor
public class AdminUsuariosServicio {

    private final UsuarioRepositorio usuarios;
    private final UsuarioRolRepositorio usuarioRoles;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public UsuarioAdminRespuesta obtener(Long usuarioId) {
        exigirAdministrador();
        return detalleDe(buscarOLanzar(usuarioId));
    }

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private Usuario buscarOLanzar(Long usuarioId) {
        return usuarios.findWithPersonaById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta ya no existe."));
    }

    private UsuarioAdminRespuesta detalleDe(Usuario usuario) {
        List<UsuarioRolFila> filas = usuarioRoles.buscarPorUsuario(usuario.getId());
        String concedidoPorNombre = filas.stream()
                .filter(f -> f.rolCodigo().equals(RolUsuarioAdmin.ADMINISTRADOR.codigo()))
                .findFirst()
                .map(UsuarioRolFila::asignadoPorUsuarioId)
                .flatMap(usuarios::findWithPersonaById)
                .map(u -> u.getPersona().nombreCompleto())
                .orElse(null);
        return mapear(usuario, filas, concedidoPorNombre);
    }

    private UsuarioAdminRespuesta mapear(Usuario usuario, List<UsuarioRolFila> filas, String concedidoPorNombre) {
        Persona persona = usuario.getPersona();
        List<RolUsuarioAdmin> rolesDeUsuario = filas.stream()
                .map(f -> RolUsuarioAdmin.desdeCodigo(f.rolCodigo()))
                .toList();
        RolUsuarioAdmin principal = filas.stream()
                .filter(UsuarioRolFila::principal)
                .findFirst()
                .map(f -> RolUsuarioAdmin.desdeCodigo(f.rolCodigo()))
                .orElse(null);
        CondicionCuentaAdmin condicion = CondicionCuentaAdmin.de(
                usuario.getCorreoVerificadoEn() != null, usuario.isRequiereCambioContrasena());

        return new UsuarioAdminRespuesta(
                usuario.getId(),
                persona.getNombres(),
                persona.getApellidoPaterno(),
                persona.getApellidoMaterno(),
                persona.nombreCompleto(),
                usuario.getCorreo(),
                persona.getTelefono(),
                persona.getDocumentoIdentidad(),
                usuario.getOrigenRegistro(),
                usuario.isActivo(),
                condicion,
                principal,
                rolesDeUsuario,
                usuario.getCreadoEn(),
                concedidoPorNombre);
    }
}
