package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ReprogramarSesionRequest(
        @NotNull Instant nuevaFechaHoraInicio,
        @NotNull Instant nuevaFechaHoraFin,
        @NotBlank String motivo) {
}
