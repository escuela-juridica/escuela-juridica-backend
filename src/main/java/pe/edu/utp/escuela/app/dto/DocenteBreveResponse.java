package pe.edu.utp.escuela.app.dto;

public record DocenteBreveResponse(
        Long personaId,
        String nombreCompleto,
        String iniciales,
        String fotoUrl,
        String cargoProfesional) {
}
