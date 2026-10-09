package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** HU-019: el pago manual y la exoneracion comparten la misma matricula. */
public record CrearMatriculaAdministrativaPeticion(
        @NotNull Long usuarioId,
        @NotNull Long cursoId,
        @NotBlank String condicionEconomica,
        BigDecimal importe,
        String medio,
        String referencia,
        @NotBlank String motivo,
        boolean confirmoAdvertenciaAcademica) {
}
