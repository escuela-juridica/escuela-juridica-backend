package pe.edu.utp.escuela.app.dto;

public record SesionAccesoResponse(
        Long usuarioId,
        String nombreCompleto,
        String correo,
        String rolPrincipal,
        boolean requiereCambioContrasena) {
}
