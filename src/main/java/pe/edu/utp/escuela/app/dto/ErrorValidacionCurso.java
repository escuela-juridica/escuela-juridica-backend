package pe.edu.utp.escuela.app.dto;

/** HU-015 — Un hallazgo al validar un curso antes de publicarlo. "ERROR" bloquea la publicación;
 * "ADVERTENCIA" (p. ej. duración de video fuera de 10–15 minutos) solo informa. */
public record ErrorValidacionCurso(
        String codigo,
        String seccion,
        String campo,
        String severidad,
        String mensaje) {
}
