package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record SesionCalendarioResponse(
        Long leccionId,
        Long cursoId,
        String cursoTitulo,
        String titulo,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String estadoSesion,
        String enlaceReunion,
        boolean grabacionDisponible) {
}
