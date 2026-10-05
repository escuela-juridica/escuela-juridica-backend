package pe.edu.utp.escuela.app.maestra;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReglaArchivoPeticion(
        @NotNull Long tipoMaterialId,
        @NotBlank @Size(max = 15) String extension,
        @NotNull @Min(1) Long tamanoMaximoBytes) {
}
