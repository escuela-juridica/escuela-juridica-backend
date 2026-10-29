package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HU-033 — Un certificado del alumno autenticado. {@code estado} es VIGENTE o ANULADO; solo uno
 * VIGENTE trae {@code descargaUrl} (ruta relativa al API): un anulado se lista como no válido y su PDF
 * deja de estar disponible. {@code entidad} es {@code null} cuando lo emite la Escuela Jurídica.
 */
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
