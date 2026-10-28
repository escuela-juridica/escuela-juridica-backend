package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** HU-028 / HU-029 — Respuesta del alumno a una pregunta: alternativas marcadas o texto abierto.
 * Guardar es idempotente: reemplaza lo que hubiera antes para esa pregunta. */
public record GuardarRespuestaPeticion(
        List<Long> opcionesIds,
        @Size(max = 8000, message = "La respuesta no puede superar los 8000 caracteres.") String texto) {
}
