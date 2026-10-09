package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.util.List;

public record UsuarioAdminRespuesta(
        Long usuarioId,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String nombreCompleto,
        String correo,
        String telefono,
        String documentoIdentidad,
        String origenRegistro,
        boolean activo,
        CondicionCuentaAdmin condicion,
        RolUsuarioAdmin rolPrincipal,
        List<RolUsuarioAdmin> roles,
        Instant creadoEn,
        /** Nombre de quién concedió el rol ADMINISTRADOR; null si la cuenta no lo tiene o se
         * asignó sin un administrador identificado. */
        String concedidoPorNombre) {
}
