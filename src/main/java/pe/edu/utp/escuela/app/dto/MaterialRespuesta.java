package pe.edu.utp.escuela.app.dto;

public record MaterialRespuesta(
        Long id,
        String titulo,
        int orden,
        boolean permiteDescarga,
        boolean activo,
        RecursoRespuesta recurso) {
}
