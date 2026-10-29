package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HU-042 — Una fila del reporte de pagos registrados. {@code fecha} es la del resultado (Culqi o registro
 * administrativo) o, mientras sigue PENDIENTE, la del inicio del intento. {@code origen}: EN_LINEA, MANUAL o
 * EXONERADO. No incluye tokens, llaves ni datos de tarjeta; solo los últimos cuatro dígitos si se informaron.
 */
public record ReportePagoResponse(
        Long pagoId,
        Instant fecha,
        String alumno,
        String correo,
        Long cursoId,
        String curso,
        BigDecimal monto,
        String medio,
        String estado,
        String numeroPedido,
        String referencia,
        String origen,
        String motivo,
        boolean suma) {
}
