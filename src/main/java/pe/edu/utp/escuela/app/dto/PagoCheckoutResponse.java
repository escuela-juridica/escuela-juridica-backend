package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;

public record PagoCheckoutResponse(
        String numeroPedido,
        BigDecimal importe,
        String moneda,
        boolean reanudado,
        String llavePublica,
        String ordenId,
        long montoCentimos,
        String titulo,
        String correo,
        String nombres,
        String apellidos) {
}
