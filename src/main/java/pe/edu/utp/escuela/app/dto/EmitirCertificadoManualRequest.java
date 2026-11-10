package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EmitirCertificadoManualRequest(
        @NotBlank @Size(min = 10, max = 1000, message = "Explica el motivo (mínimo 10 caracteres).") String motivo,
        LocalDate fechaFinalizacion,
        @Size(max = 240, message = "Los nombres impresos admiten hasta 240 caracteres.") String nombres) {
}
