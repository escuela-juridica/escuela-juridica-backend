package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record GuardarRespuestaRequest(
        List<Long> opcionesIds,
        @Size(max = 8000, message = "La respuesta no puede superar los 8000 caracteres.") String texto) {
}
