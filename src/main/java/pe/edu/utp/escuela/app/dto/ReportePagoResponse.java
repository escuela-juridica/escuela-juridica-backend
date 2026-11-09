package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

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
