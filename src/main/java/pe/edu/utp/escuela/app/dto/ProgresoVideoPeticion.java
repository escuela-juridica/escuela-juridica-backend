package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** HU-024 — Registra el avance de reproducción de un material de video. */
public record ProgresoVideoPeticion(
        @NotNull @Min(0) Integer segundosReproducidos,
        @NotNull @Min(0) Integer ultimaPosicionSegundos) {
}
