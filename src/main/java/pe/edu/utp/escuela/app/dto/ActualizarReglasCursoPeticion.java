package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos editables de HU-014. Las relaciones entre campos se validan en el servicio. */
public record ActualizarReglasCursoPeticion(
        boolean requiereExamenes,
        boolean requiereProgreso,
        boolean requiereAsistencia,
        @NotNull @DecimalMin("0.00") @DecimalMax("20.00") BigDecimal notaMinima,
        @NotNull @DecimalMin("0.00") @DecimalMax("20.00") BigDecimal notaRefrendado,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal progresoMinimo,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal umbralVideo,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal asistenciaMinima,
        boolean secuenciaObligatoria,
        @Min(0) int diasEsperaCertificado,
        LocalDate fechaCierreMatricula) {
}
