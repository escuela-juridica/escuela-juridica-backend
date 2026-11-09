package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IntentoResponse(
        Long id,
        Long examenId,
        Long cursoId,
        String examenTitulo,
        String tipo,
        int numero,
        String estado,
        Instant iniciadoEn,
        Instant expiraEn,
        Long segundosRestantes,
        Instant ahora,
        BigDecimal puntajeTotal,
        BigDecimal puntajeObtenido,
        BigDecimal nota,
        Boolean aprobado,
        BigDecimal notaMinima,
        Instant enviadoEn,
        boolean cerradoPorTiempo,
        Instant fechaLimiteRevision,
        boolean respuestasVisibles,
        boolean afectaCertificacion,
        List<PreguntaIntentoResponse> preguntas) {

    public IntentoResponse {
        preguntas = List.copyOf(preguntas);
    }
}
