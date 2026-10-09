package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record ModuloRespuesta(
        Long id,
        String titulo,
        String descripcion,
        int orden,
        boolean activo,
        Long moduloOrigenId,
        List<LeccionRespuesta> lecciones) {
}
