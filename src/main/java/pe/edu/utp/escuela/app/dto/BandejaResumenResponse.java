package pe.edu.utp.escuela.app.dto;

/** HU-030 — Cuántos intentos hay por revisar y cuántos están próximos a vencer o ya vencidos. */
public record BandejaResumenResponse(long pendientes, long proximos, long vencidos) {
}
