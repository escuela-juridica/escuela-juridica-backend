package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PagoMatriculaDetalleRespuesta(
        Long pagoId, String origen, String estado, BigDecimal importe, String moneda,
        String medio, String referencia, String motivo, Long registradoPorUsuarioId,
        String registradoPor, Instant resultadoEn) {}
