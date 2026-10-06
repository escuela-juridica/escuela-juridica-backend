package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarMaterialPeticion(
        @NotBlank @Size(max = 220) String titulo,
        boolean permiteDescarga) {
}
