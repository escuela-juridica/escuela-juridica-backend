package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;

/**
 * HU-047 — Todo lo que el navegador necesita para abrir Culqi Checkout con este intento. {@code reanudado} indica
 * que ya existía un intento PENDIENTE y se continúa con él (nunca hay dos simultáneos).
 */
public record PagoCheckoutResponse(
        String numeroPedido,
        BigDecimal importe,
        String moneda,
        boolean reanudado,
        String modo,
        String llavePublica,
        String ordenId,
        long montoCentimos,
        String titulo,
        String correo,
        String nombres,
        String apellidos) {
}
