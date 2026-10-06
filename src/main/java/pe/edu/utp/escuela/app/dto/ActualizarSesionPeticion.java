package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** HU-012 — Programa la sesión de una lección EN_VIVO: fecha, ventana horaria y enlace de
 * reunión. {@code enlaceReunion} es opcional porque el horario puede fijarse antes que el enlace;
 * ESEJUR solo lo conserva, nunca crea ni administra la reunión en el proveedor externo. */
public record ActualizarSesionPeticion(
        @NotNull Instant fechaHoraInicio,
        @NotNull Instant fechaHoraFin,
        String enlaceReunion) {
}
