package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-032 — Nombres y apellidos que el alumno confirma para imprimirlos en su certificado (los tres obligatorios). */
public record ConfirmarDatosRequest(
        @NotBlank(message = "Indica tus nombres.") @Size(max = 120, message = "Los nombres admiten hasta 120 caracteres.") String nombres,
        @NotBlank(message = "Indica tu apellido paterno.") @Size(max = 80, message = "El apellido paterno admite hasta 80 caracteres.") String apellidoPaterno,
        @NotBlank(message = "Indica tu apellido materno.") @Size(max = 80, message = "El apellido materno admite hasta 80 caracteres.") String apellidoMaterno) {
}
