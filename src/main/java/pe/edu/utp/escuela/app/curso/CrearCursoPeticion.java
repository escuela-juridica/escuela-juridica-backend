package pe.edu.utp.escuela.app.curso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Crea el borrador mínimo" (mapa técnico HU-010): el resto se completa en la pestaña
 * Información tras la creación. */
public record CrearCursoPeticion(
        @NotBlank @Size(max = 220) String titulo) {
}
