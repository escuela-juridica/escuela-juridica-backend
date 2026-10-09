package pe.edu.utp.escuela.app.dto;

public record DocenteCursoRespuesta(
        Long personaId,
        String nombreCompleto,
        String fotoUrl,
        String cargoProfesional,
        int orden,
        boolean activo) {
}
