package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.util.List;

public record LeccionRespuesta(
        Long id,
        String titulo,
        String descripcion,
        int orden,
        String tipo,
        boolean esObligatoria,
        boolean esVistaPrevia,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String enlaceReunion,
        String estado,
        String motivoCancelacion,
        boolean activo,
        Long leccionOrigenId,
        List<MaterialRespuesta> materiales) {
}
