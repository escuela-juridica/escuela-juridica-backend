package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** HU-016 — Antes de que un curso PUBLICADO inicie, retrasa su fecha de inicio. */
public record RetrasarInicioPeticion(@NotNull LocalDate nuevaFechaInicio) {
}
