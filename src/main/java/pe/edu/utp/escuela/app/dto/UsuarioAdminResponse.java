package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.util.List;

public record UsuarioAdminResponse(
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
        String concedidoPorNombre) {
}
