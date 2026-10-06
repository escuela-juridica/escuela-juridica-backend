package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoriaPeticion(
        @NotBlank @Size(max = 50) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Integer orden) {
}
