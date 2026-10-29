package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** HU-035 — Motivo obligatorio y, solo si el alumno aún no tiene fecha de finalización, una fecha válida. */
public record EmitirCertificadoManualRequest(
        @NotBlank @Size(min = 10, max = 1000, message = "Explica el motivo (mínimo 10 caracteres).") String motivo,
        LocalDate fechaFinalizacion) {
}
