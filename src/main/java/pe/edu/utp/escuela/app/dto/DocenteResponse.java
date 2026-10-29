package pe.edu.utp.escuela.app.dto;

public record DocenteResponse(
        Long personaId,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String nombreCompleto,
        String fotoUrl,
        String cargoProfesional,
        String biografiaProfesional,
        boolean activo) {
}
