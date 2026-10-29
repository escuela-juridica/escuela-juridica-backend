package pe.edu.utp.escuela.app.dto;

public record DocenteFichaResponse(
        Long personaId,
        String nombreCompleto,
        String fotoUrl,
        String cargoProfesional,
        String biografiaProfesional) {
}
