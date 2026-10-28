package pe.edu.utp.escuela.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** HU-030 — Publica la calificación completa de un intento: un puntaje por cada respuesta
 * abierta pendiente, entre 0 y el máximo de su pregunta (ambos extremos válidos). */
public record CalificarIntentoRequest(
        @NotEmpty(message = "Indica el puntaje de cada respuesta.") List<@Valid PuntajeRespuestaRequest> respuestas) {

    public record PuntajeRespuestaRequest(
            @NotNull Long respuestaId,
            @NotNull(message = "Indica un puntaje.")
            @DecimalMin(value = "0", message = "El puntaje no puede ser menor que cero.") BigDecimal puntaje,
            @Size(max = 2000, message = "La observación no puede superar los 2000 caracteres.") String observacion) {
    }
}
