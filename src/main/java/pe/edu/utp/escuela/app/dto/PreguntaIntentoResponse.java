package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * HU-028 / HU-029 — Una pregunta dentro de un intento, con lo que el alumno ya respondió.
 * {@code puntajeObtenido}, {@code correcta} y {@code estadoRevision} solo se informan en un
 * intento ya enviado. {@code correcta} es {@code null} en preguntas abiertas.
 */
public record PreguntaIntentoResponse(
        Long id,
        String tipo,
        String enunciado,
        BigDecimal puntaje,
        int posicion,
        List<OpcionIntentoResponse> opciones,
        List<Long> seleccionadas,
        String texto,
        boolean respondida,
        BigDecimal puntajeObtenido,
        Boolean correcta,
        String estadoRevision) {

    public PreguntaIntentoResponse {
        opciones = List.copyOf(opciones);
        seleccionadas = List.copyOf(seleccionadas);
    }
}
