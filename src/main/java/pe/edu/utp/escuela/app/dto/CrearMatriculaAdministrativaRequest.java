package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CrearMatriculaAdministrativaRequest(
        @NotNull Long usuarioId,
        @NotNull Long cursoId,
        @NotBlank String condicionEconomica,
        BigDecimal importe,
        String medio,
        String referencia,
        @NotBlank String motivo,
        boolean confirmoAdvertenciaAcademica) {
}
