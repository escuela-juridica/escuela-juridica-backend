package pe.edu.utp.escuela.app.dto;

public record ReclamacionPrecargaResponse(
        String nombres,
        String apellidos,
        String correo,
        String celular,
        String tipoDocumento,
        String numeroDocumento) {
}
