package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearOpcionPeticion(
        @NotBlank @Size(max = 500) String texto,
        boolean esCorrecta) {
}
