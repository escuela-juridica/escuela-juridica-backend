package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HU-034 — Lo único que se muestra públicamente de un certificado. Nunca incluye documento de
 * identidad, correo, teléfono, nota, firmas ni el PDF, ni el motivo de una anulación. {@code estado}
 * es VIGENTE o ANULADO ({@code anuladoEn} solo en este último caso).
 */
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
