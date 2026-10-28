package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** HU-027 — Una fila del historial de cambios de una sesión (reprogramación o cancelación). */
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
