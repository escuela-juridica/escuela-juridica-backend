package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
