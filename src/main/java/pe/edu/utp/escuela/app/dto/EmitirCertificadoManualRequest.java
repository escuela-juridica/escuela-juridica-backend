package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * HU-035 — Motivo obligatorio y, solo si el alumno aún no tiene fecha de finalización, una fecha válida.
 * {@code nombres}: los nombres y apellidos que se imprimirán; solo se usan cuando el alumno todavía no los confirmó
 * (si se omiten, se usa el nombre completo de su cuenta). La administración los confirma en su lugar.
 */
public record EmitirCertificadoManualRequest(
        @NotBlank @Size(min = 10, max = 1000, message = "Explica el motivo (mínimo 10 caracteres).") String motivo,
        LocalDate fechaFinalizacion,
        @Size(max = 240, message = "Los nombres impresos admiten hasta 240 caracteres.") String nombres) {
}
