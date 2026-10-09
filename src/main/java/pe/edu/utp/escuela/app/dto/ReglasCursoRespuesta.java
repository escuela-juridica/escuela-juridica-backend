package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Configuración académica que controla la certificación de una convocatoria. */
public record ReglasCursoRespuesta(
        boolean requiereExamenes,
        boolean requiereProgreso,
        boolean requiereAsistencia,
        BigDecimal notaMinima,
        BigDecimal notaRefrendado,
        BigDecimal progresoMinimo,
        BigDecimal umbralVideo,
        BigDecimal asistenciaMinima,
        boolean secuenciaObligatoria,
        int diasEsperaCertificado,
        LocalDate fechaCierreMatricula,
        boolean bloqueada) {
}
