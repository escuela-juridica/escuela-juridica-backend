package pe.edu.utp.escuela.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CrearPreguntaRequest(
        @NotNull TipoPregunta tipo,
        @NotBlank String enunciado,
        @NotNull @DecimalMin(value = "0.01") BigDecimal puntaje,
        @Valid List<CrearOpcionRequest> opciones) {
}
