package pe.edu.utp.escuela.app.dto;

public record PerfilResponse(
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String correo,
        String telefono,
        String documentoIdentidad,
        String fotoUrl,
        boolean accesoGoogle,
        boolean puedeCrearContrasena) {
}
