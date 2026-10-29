package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** HU-027 — Reprograma una sesión EN_VIVO futura: conserva el horario anterior en el historial. */
public record ReprogramarSesionRequest(
        @NotNull Instant nuevaFechaHoraInicio,
        @NotNull Instant nuevaFechaHoraFin,
        @NotBlank String motivo) {
}
