package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-038 — Cancelación completa: exige motivo y confirmación explícita del impacto. */
public record CancelarCursoRequest(
        @NotBlank(message = "Explica el motivo de la cancelación.")
        @Size(min = 10, max = 2000, message = "Explica el motivo con entre 10 y 2000 caracteres.")
        String motivo,
        @AssertTrue(message = "Confirma que entiendes el impacto de cancelar el curso.")
        boolean confirmo) {
}
