package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record HistorialSesionResponse(
        String accion,
        Instant inicioAnterior,
        Instant finAnterior,
        Instant inicioNuevo,
        Instant finNuevo,
        String motivo,
        Long realizadoPorUsuarioId,
        Instant realizadoEn) {
}
