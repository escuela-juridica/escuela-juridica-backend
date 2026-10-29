package pe.edu.utp.escuela.app.dto;

/** HU-025 — Una condición activa del certificado, con su valor real y su meta. {@code codigo} es
 * PROGRESO, EXAMENES, NOTA o ASISTENCIA. */
public record CondicionAvanceResponse(
        String codigo,
        String titulo,
        String valorActual,
        String meta,
        boolean cumplida) {
}
