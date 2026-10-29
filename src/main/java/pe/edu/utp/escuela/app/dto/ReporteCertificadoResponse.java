package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-044 — Una fila del reporte de certificados emitidos, con los datos vigentes del certificado.
 * {@code forma}: AUTOMATICA (emisión programada), SOLICITADA (confirmada por el alumno) o MANUAL
 * (administrativa). No incluye documento de identidad, nota, firmas ni el PDF.
 */
public record ReporteCertificadoResponse(
        Long certificadoId,
        String codigo,
        String alumno,
        Long cursoId,
        String curso,
        String nivel,
        String entidad,
        Instant fechaFinalizacion,
        Instant fechaEmision,
        String estado,
        String forma) {
}
