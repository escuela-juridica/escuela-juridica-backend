package pe.edu.utp.escuela.app.dto;

/** Para elegir qué módulo copiar dentro de otro curso ("agregar módulo existente"). */
public record ModuloDisponibleRespuesta(
        Long id,
        String titulo,
        Long cursoId,
        String cursoTitulo,
        long cantidadLecciones) {
}
