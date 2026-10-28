package pe.edu.utp.escuela.app.dto;

/** HU-028 — Alternativa de una pregunta dentro de un intento. {@code esCorrecta} solo se informa
 * cuando las respuestas ya pueden mostrarse; mientras tanto es {@code null}. */
public record OpcionIntentoRespuesta(Long id, String texto, Boolean esCorrecta) {
}
