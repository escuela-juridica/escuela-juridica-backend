package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ConstanciaPagoResponse(
        String constanciaNumero,
        String numeroPedido,
        BigDecimal importe,
        String moneda,
        String medio,
        String ultimosDigitos,
        Instant fecha,
        String alumno,
        String correo,
        String curso,
        Long cursoId) {
}
