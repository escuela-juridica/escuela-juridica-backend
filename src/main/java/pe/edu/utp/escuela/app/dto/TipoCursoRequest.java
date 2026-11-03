package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TipoCursoRequest(
        @NotBlank @Size(max = 50) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Integer orden,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2,4}", message = "La sigla debe tener de 2 a 4 letras.") String siglaRegistro) {
}
