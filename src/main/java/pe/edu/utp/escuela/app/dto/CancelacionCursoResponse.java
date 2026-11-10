package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

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
