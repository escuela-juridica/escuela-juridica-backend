package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record HistorialEstadoMatriculaResponse(
        String estadoAnterior, String estadoNuevo, String motivo,
        Long realizadoPorUsuarioId, String realizadoPor, Instant realizadoEn) {}
