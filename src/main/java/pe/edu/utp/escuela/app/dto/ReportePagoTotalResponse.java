package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;

public record ReportePagoTotalResponse(BigDecimal totalConfirmado, long cantidadConfirmados, String moneda) {
}
