package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** HU-012 — Programa la sesión de una lección EN_VIVO: fecha, ventana horaria y enlace de
 * reunión. Escuela Juridica conserva el enlace, pero no crea ni administra la reunión en el
 * proveedor externo. */
public record ActualizarSesionPeticion(
        @NotNull Instant fechaHoraInicio,
        @NotNull Instant fechaHoraFin,
        @NotBlank String enlaceReunion) {
}
