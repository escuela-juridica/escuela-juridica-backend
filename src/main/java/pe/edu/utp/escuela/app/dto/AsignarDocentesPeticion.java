package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** El orden en la lista es el orden de presentación; no hace falta mandarlo aparte. */
public record AsignarDocentesPeticion(@NotEmpty List<Long> personaIds) {
}
