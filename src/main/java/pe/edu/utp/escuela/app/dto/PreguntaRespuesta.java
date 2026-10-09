package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record PreguntaRespuesta(
        Long id,
        String tipo,
        String enunciado,
        BigDecimal puntaje,
        int orden,
        boolean activo,
        List<OpcionRespuesta> opciones) {
}
