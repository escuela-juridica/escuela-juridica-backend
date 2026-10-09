package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoMaterialPeticion(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 100) String nombre,
        String descripcion) {
}
