package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-036 — Anulación irreversible: exige motivo y confirmación explícita. */
public record AnularCertificadoRequest(
        @NotBlank(message = "Explica el motivo de la anulación.")
        @Size(min = 10, max = 2000, message = "Explica el motivo con entre 10 y 2000 caracteres.")
        String motivo,
        @AssertTrue(message = "Confirma que entiendes que la anulación es irreversible.")
        boolean confirmo) {
}
