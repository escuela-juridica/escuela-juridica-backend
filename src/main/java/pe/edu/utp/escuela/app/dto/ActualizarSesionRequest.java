package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ActualizarSesionRequest(
        @NotNull Instant fechaHoraInicio,
        @NotNull Instant fechaHoraFin,
        @NotBlank String enlaceReunion) {
}
