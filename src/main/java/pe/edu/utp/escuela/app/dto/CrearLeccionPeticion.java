package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code fechaHoraInicio}/{@code fechaHoraFin}: programación informativa para EN_VIVO, mostrada
 * ya en la ficha pública (HU-007); la gestión operativa de la sesión (enlace, cancelación,
 * asistencia) es de HU-012. */
public record CrearLeccionPeticion(
        @NotBlank @Size(max = 220) String titulo,
        String descripcion,
        @NotNull TipoLeccion tipo,
        boolean esObligatoria,
        boolean esVistaPrevia,
        Instant fechaHoraInicio,
        Instant fechaHoraFin) {
}
