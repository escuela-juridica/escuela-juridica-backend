package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Crea solo el contenido de la lección. Una lección EN_VIVO nace sin programar: su fecha, hora
 * y enlace se registran aparte con {@link ActualizarSesionPeticion} (HU-012), una vez que existe
 * la lección sobre la cual programar la sesión. */
public record CrearLeccionPeticion(
        @NotBlank @Size(max = 220) String titulo,
        String descripcion,
        @NotNull TipoLeccion tipo,
        boolean esObligatoria,
        boolean esVistaPrevia) {
}
