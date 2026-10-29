package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-038 — Impacto de cancelar un curso por completo y, una vez cancelado, el estado de la atención a sus
 * afectados. {@code puedeCancelar} es verdadero solo para PUBLICADO o EN_CURSO; si no, {@code bloqueo} explica por qué.
 * ESEJUR no devuelve dinero: los pagos los resuelve la Escuela fuera de la plataforma.
 */
public record CancelacionCursoResponse(
        Long cursoId,
        String titulo,
        String estado,
        boolean puedeCancelar,
        String bloqueo,
        long alumnosConAcceso,
        long alumnosFinalizados,
        long alumnosSinFinalizar,
        long matriculasPendientePago,
        long pagosEnProceso,
        long sesionesFuturas,
        Instant canceladoEn,
        String motivoCancelacion,
        long avisosTotal,
        long avisosEnviados,
        long avisosConError) {
}
