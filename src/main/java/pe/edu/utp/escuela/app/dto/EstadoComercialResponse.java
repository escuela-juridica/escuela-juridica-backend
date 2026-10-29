package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EstadoComercialResponse(
        String codigo,
        String etiqueta,
        LocalDate fechaInicio,
        boolean matriculaPermitida,
        BigDecimal precioActual,
        BigDecimal precioRegular,
        boolean promocionActiva,
        String accion) {
}
