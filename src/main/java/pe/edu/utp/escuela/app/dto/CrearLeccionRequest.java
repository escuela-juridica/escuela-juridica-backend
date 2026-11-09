package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearLeccionRequest(
        @NotBlank @Size(max = 220) String titulo,
        String descripcion,
        @NotNull TipoLeccion tipo,
        boolean esObligatoria,
        boolean esVistaPrevia) {
}
