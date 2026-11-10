package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

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
