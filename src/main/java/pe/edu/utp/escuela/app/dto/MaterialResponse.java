package pe.edu.utp.escuela.app.dto;

public record MaterialResponse(
        Long id,
        String titulo,
        int orden,
        boolean permiteDescarga,
        boolean activo,
        RecursoResponse recurso) {
}
