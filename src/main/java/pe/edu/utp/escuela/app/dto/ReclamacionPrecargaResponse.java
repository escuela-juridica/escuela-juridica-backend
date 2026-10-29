package pe.edu.utp.escuela.app.dto;

/** HU-039 — Datos conocidos de la persona con sesión, para prellenar el formulario (ella puede corregirlos). */
public record ReclamacionPrecargaResponse(
        String nombres,
        String apellidos,
        String correo,
        String celular,
        String tipoDocumento,
        String numeroDocumento) {
}
