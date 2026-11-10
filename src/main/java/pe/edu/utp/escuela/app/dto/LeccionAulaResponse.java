package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record LeccionAulaResponse(
        Long id,
        String titulo,
        String tipo,
        int orden,
        boolean esObligatoria,
        String estado,
        String motivoBloqueo,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String estadoSesion,
        boolean grabacionDisponible,
        String descripcion,
        Integer duracionSegundos) {
}
