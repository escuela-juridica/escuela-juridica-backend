package pe.edu.utp.escuela.app.dto;

public record DocenteCursoRow(
        Long cursoId,
        Long personaId,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String fotoUrl,
        String cargoProfesional,
        Integer orden) {
}
