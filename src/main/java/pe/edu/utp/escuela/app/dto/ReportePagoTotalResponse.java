package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;

/**
 * HU-042 — Total de pagos confirmados dentro de los filtros: solo APROBADO y REGISTRADO_MANUAL con monto mayor que
 * cero. PENDIENTE, RECHAZADO, ERROR, EXPIRADO y EXONERADO aparecen en el detalle pero no suman. No es contabilidad
 * ni un comprobante tributario: no calcula impuestos, comisiones ni devoluciones.
 */
public record ReportePagoTotalResponse(BigDecimal totalConfirmado, long cantidadConfirmados, String moneda) {
}
