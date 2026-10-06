package pe.edu.utp.escuela.app.dto;

import java.util.List;

/** HU-015 — Resultado de validar o de intentar publicar un curso. {@code publicacionRealizada}
 * distingue una simple consulta (GET validación) de una publicación efectivamente aplicada (POST
 * publicación); {@code estadoCodigo} refleja el estado del curso después de la operación. */
public record ValidacionPublicacionRespuesta(
        boolean puedePublicarse,
        List<ErrorValidacionCurso> hallazgos,
        boolean publicacionRealizada,
        String estadoCodigo) {
}
