package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PagoIniciarRequest(@NotNull BigDecimal importeConfirmado) {
}
