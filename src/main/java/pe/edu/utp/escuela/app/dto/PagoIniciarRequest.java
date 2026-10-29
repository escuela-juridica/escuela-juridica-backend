package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** HU-047 — El alumno confirma el importe vigente que vio antes de enviar la operación a Culqi. */
public record PagoIniciarRequest(@NotNull BigDecimal importeConfirmado) {
}
