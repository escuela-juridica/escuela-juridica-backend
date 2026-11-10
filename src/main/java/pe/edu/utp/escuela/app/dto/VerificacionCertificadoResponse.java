package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record VerificacionCertificadoResponse(
        String estado,
        String codigo,
        String titular,
        String curso,
        BigDecimal horas,
        String nivel,
        String entidad,
        Instant emitidoEn,
        Instant anuladoEn) {
}
