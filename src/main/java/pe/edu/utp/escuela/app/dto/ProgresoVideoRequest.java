package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProgresoVideoRequest(
        @NotNull @Min(0) Integer segundosReproducidos,
        @NotNull @Min(0) Integer ultimaPosicionSegundos,
        @Min(1) Integer duracionSegundosTotal) {
}
