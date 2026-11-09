package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearMaterialEnlaceRequest(
        @NotBlank @Size(max = 220) String titulo,
        @NotNull Long tipoMaterialId,
        @NotNull OrigenRecurso origen,
        @NotBlank String referencia,
        Boolean youtubeNoListadoConfirmado,
        boolean permiteDescarga,
        Integer duracionSegundos) {
}
