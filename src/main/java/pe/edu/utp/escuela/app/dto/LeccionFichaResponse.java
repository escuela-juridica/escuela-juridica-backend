package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record LeccionFichaResponse(
        Long leccionId,
        String titulo,
        Integer orden,
        String tipo,
        String estado,
        boolean esVistaPrevia,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        Integer duracionSegundos) {
}
