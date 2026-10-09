package pe.edu.utp.escuela.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** {@code opciones}: ignoradas para RESPUESTA_ABIERTA (no tiene calificación automática);
 * reemplazan por completo a las anteriores en cada guardado, igual que el resto de HU-013 no
 * crea intentos ni respuestas todavía que dependan de ellas. */
public record CrearPreguntaPeticion(
        @NotNull TipoPregunta tipo,
        @NotBlank String enunciado,
        @NotNull @DecimalMin(value = "0.01") BigDecimal puntaje,
        @Valid List<CrearOpcionPeticion> opciones) {
}
