package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HU-047 — Un intento de pago tal como lo ve el alumno. Solo un intento APROBADO tiene constancia; el mensaje
 * nunca afirma que se realizó un pago que Culqi no aprobó.
 */
public record PagoDetalleResponse(
        String numeroPedido,
        Long cursoId,
        String cursoTitulo,
        String cursoUrlAmigable,
        String estado,
        String medio,
        String ultimosDigitos,
        Integer numeroCuotas,
        BigDecimal importe,
        String moneda,
        BigDecimal precioRegular,
        BigDecimal precioPromocional,
        String mensaje,
        Instant iniciadoEn,
        Instant resultadoEn,
        String constanciaNumero,
        String estadoMatricula,
        boolean accesoConcedido,
        boolean puedeReintentar,
        InstruccionesPagoResponse instrucciones,
        String estadoCorreo) {
}
