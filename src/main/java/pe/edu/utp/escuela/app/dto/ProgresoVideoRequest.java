package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** HU-024 — Registra el avance de reproducción de un material de video.
 *
 * <p>{@code duracionSegundosTotal} es opcional: el navegador la conoce apenas carga los metadatos
 * del video ({@code <video>.duration}), algo que el backend no puede medir por sí mismo para un
 * archivo SUBIDO (no hay procesamiento de video en el servidor). La primera vez que llega un valor
 * aquí para un recurso sin duración registrada, el backend la adopta como oficial. */
public record ProgresoVideoRequest(
        @NotNull @Min(0) Integer segundosReproducidos,
        @NotNull @Min(0) Integer ultimaPosicionSegundos,
        @Min(1) Integer duracionSegundosTotal) {
}
