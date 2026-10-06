package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Lista completa y ordenada de identificadores; el orden final es la posición dentro de la
 * lista (reutilizada para módulos, lecciones y materiales). */
public record OrdenPeticion(@NotEmpty List<Long> ids) {
}
