package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.util.List;

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
        String estadoRevision,
        String observacion) {

    public PreguntaIntentoResponse {
        opciones = List.copyOf(opciones);
        seleccionadas = List.copyOf(seleccionadas);
    }
}
