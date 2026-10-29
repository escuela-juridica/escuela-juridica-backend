package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * HU-036 — Corrección de datos documentales: nombres, título, horas, entidad y firmantes. La nota y el nivel
 * congelados no se corrigen libremente: si llegan y difieren de los vigentes, la corrección se rechaza.
 */
public record CorregirCertificadoRequest(
        @NotBlank(message = "Explica el motivo de la corrección.")
        @Size(min = 10, max = 2000, message = "Explica el motivo con entre 10 y 2000 caracteres.")
        String motivo,
        @NotBlank(message = "Indica los nombres que se imprimirán.")
        @Size(max = 240, message = "Los nombres no pueden superar 240 caracteres.")
        String nombres,
        @NotBlank(message = "Indica el título del curso.")
        @Size(max = 220, message = "El título no puede superar 220 caracteres.")
        String titulo,
        @NotNull(message = "Indica las horas académicas.")
        @DecimalMin(value = "0.5", message = "Las horas académicas deben ser mayores que cero.")
        @DecimalMax(value = "9999", message = "Las horas académicas no son válidas.")
        BigDecimal horas,
        @Size(max = 220, message = "La entidad no puede superar 220 caracteres.")
        String entidad,
        @NotEmpty(message = "Elige al menos un firmante.")
        @Size(max = 4, message = "Puedes imprimir hasta 4 firmantes.")
        List<Long> firmanteIds,
        String nivel,
        BigDecimal nota) {
}
