package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RetrasarInicioRequest(@NotNull LocalDate nuevaFechaInicio) {
}
