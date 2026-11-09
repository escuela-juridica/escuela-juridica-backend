package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MiCertificadoResponse(
        String codigo,
        String titular,
        Long cursoId,
        String curso,
        String nivel,
        String entidad,
        BigDecimal horas,
        Instant emitidoEn,
        String estado,
        Instant anuladoEn,
        String descargaUrl) {
}
