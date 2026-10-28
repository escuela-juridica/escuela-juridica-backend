package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfiguracionRequest(
        @NotBlank @Size(max = 80) String codigo,
        @NotBlank String valor,
        String descripcion) {
}
