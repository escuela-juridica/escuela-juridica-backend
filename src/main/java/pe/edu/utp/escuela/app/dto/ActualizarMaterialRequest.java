package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarMaterialRequest(
        @NotBlank @Size(max = 220) String titulo,
        boolean permiteDescarga,
        @Size(max = 2048) String referencia,
        Boolean youtubeNoListadoConfirmado) {
}
