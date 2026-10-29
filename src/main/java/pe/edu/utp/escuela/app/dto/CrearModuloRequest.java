package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearModuloRequest(
        @NotBlank @Size(max = 220) String titulo,
        String descripcion) {
}
