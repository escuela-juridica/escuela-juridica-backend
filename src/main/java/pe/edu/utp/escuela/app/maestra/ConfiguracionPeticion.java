package pe.edu.utp.escuela.app.maestra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfiguracionPeticion(
        @NotBlank @Size(max = 80) String codigo,
        @NotBlank String valor,
        String descripcion) {
}
