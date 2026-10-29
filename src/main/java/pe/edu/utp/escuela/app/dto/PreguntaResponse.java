package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record PreguntaResponse(
        Long id,
        String tipo,
        String enunciado,
        BigDecimal puntaje,
        int orden,
        boolean activo,
        List<OpcionResponse> opciones) {
}
