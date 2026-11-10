package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OtorgarIntentoRequest(
        @NotNull Long examenId,
        @NotBlank @Size(min = 10, max = 1000, message = "Explica el motivo (entre 10 y 1000 caracteres).") String motivo) {
}
