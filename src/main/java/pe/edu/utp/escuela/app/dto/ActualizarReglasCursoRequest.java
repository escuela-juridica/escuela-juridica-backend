package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ActualizarReglasCursoRequest(
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
        LocalDate fechaCierreMatricula,
        @Pattern(regexp = "MEJOR|ULTIMA", message = "El criterio de nota debe ser MEJOR o ULTIMA.") String criterioNota) {

    public ActualizarReglasCursoRequest(boolean requiereExamenes, boolean requiereProgreso, boolean requiereAsistencia,
            BigDecimal notaMinima, BigDecimal notaRefrendado, BigDecimal progresoMinimo, BigDecimal umbralVideo,
            BigDecimal asistenciaMinima, boolean secuenciaObligatoria, int diasEsperaCertificado,
            LocalDate fechaCierreMatricula) {
        this(requiereExamenes, requiereProgreso, requiereAsistencia, notaMinima, notaRefrendado, progresoMinimo,
                umbralVideo, asistenciaMinima, secuenciaObligatoria, diasEsperaCertificado, fechaCierreMatricula, null);
    }
}
