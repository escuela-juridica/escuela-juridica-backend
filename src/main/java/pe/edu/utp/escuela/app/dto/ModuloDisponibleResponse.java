package pe.edu.utp.escuela.app.dto;

public record ModuloDisponibleResponse(
        Long id,
        String titulo,
        Long cursoId,
        String cursoTitulo,
        long cantidadLecciones) {
}
