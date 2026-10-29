package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * HU-028 / HU-029 — Un intento: en curso (con tiempo restante y respuestas guardadas) o ya
 * enviado (con resultado). El tiempo lo decide el servidor: {@code segundosRestantes} se calcula
 * al responder y {@code ahora} permite al navegador corregir diferencias de reloj.
 *
 * <p>Un intento PENDIENTE_REVISION no tiene nota todavía y trae {@code fechaLimiteRevision}.
 * {@code respuestasVisibles} indica si las alternativas correctas se muestran según el examen.
 */
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
