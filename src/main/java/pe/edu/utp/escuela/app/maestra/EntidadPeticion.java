package pe.edu.utp.escuela.app.maestra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EntidadPeticion(
        @NotBlank @Size(max = 200) String nombre,
        String logoUrl) {
}
