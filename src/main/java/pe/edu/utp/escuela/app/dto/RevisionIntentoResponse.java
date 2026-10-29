package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * HU-030 — Un intento para revisar: encabezado, lo que ya sumaron las preguntas objetivas y las
 * respuestas abiertas con su puntaje máximo. {@code estado} es PENDIENTE_REVISION o CALIFICADO
 * (un intento ya calificado se muestra en solo lectura). {@code nota} y {@code aprobado} solo
 * existen cuando ya está CALIFICADO.
 */
public record RevisionIntentoResponse(
        Long intentoId,
        String estado,
        Long matriculaId,
        String alumno,
        String correo,
        Long cursoId,
        String curso,
        Long examenId,
        String examen,
        String tipo,
        int numeroIntento,
        Instant enviadoEn,
        Instant fechaLimite,
        String plazo,
        BigDecimal puntajeTotal,
        BigDecimal puntajeObjetivo,
        BigDecimal notaMinima,
        BigDecimal nota,
        Boolean aprobado,
        Instant calificadoEn,
        List<PreguntaRevisionResponse> preguntas) {

    public RevisionIntentoResponse {
        preguntas = List.copyOf(preguntas);
    }

    /** Una respuesta abierta. {@code puntajeAsignado} y {@code observacion} existen si ya fue revisada. */
    public record PreguntaRevisionResponse(
            Long respuestaId,
            int posicion,
            String enunciado,
            BigDecimal puntajeMaximo,
            String texto,
            String estadoRevision,
            BigDecimal puntajeAsignado,
            String observacion,
            Instant revisadoEn) {
    }
}
