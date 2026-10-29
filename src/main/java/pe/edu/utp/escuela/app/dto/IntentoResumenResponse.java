package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** HU-028 — Una fila del historial de intentos de un alumno en un examen. */
public record IntentoResumenResponse(
        Long id,
        int numero,
        String estado,
        BigDecimal nota,
        Boolean aprobado,
        Instant iniciadoEn,
        Instant enviadoEn,
        Instant fechaLimiteRevision) {
}
