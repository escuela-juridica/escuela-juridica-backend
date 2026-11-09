package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AsignarDocentesRequest(@NotEmpty List<Long> personaIds) {
}
