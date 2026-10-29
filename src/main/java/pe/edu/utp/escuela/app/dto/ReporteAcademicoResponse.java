package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * HU-043 — Una fila del reporte de seguimiento académico: un alumno en un curso.
 *
 * <p>{@code condiciones} trae solo las condiciones ACTIVAS del curso, cada una con su valor real y
 * su meta; las desactivadas no existen aquí y nunca se presentan como pendientes. {@code
 * progreso} se muestra siempre (aunque no sea requisito). {@code asistencia} es {@code null}
 * cuando no aplica (curso virtual, no exigida o sin sesiones elegibles) y {@code notaFinal} es
 * {@code null} si el curso no exige exámenes o aún no hay notas.
 *
 * <p>{@code situacion}: EN_PROGRESO, EN_REVISION (hay una respuesta abierta sin calificar),
 * FINALIZADO o CANCELADA. {@code cumplimiento}: CUMPLE o NO_CUMPLE (todas las condiciones activas).
 * {@code certificado}: SIN_EMITIR, EMITIDO o ANULADO.
 */
public record ReporteAcademicoResponse(
        Long matriculaId,
        Long cursoId,
        String curso,
        String modalidad,
        String alumno,
        String correo,
        Instant fechaMatricula,
        String estadoMatricula,
        int progreso,
        boolean exigeExamenes,
        int examenesRequeridos,
        int examenesAprobados,
        BigDecimal notaFinal,
        Integer asistencia,
        boolean asistenciaExigida,
        long respuestasPendientes,
        Instant fechaLimiteRevision,
        List<CondicionAvanceResponse> condiciones,
        List<String> condicionesPendientes,
        String situacion,
        String cumplimiento,
        Instant fechaFinalizacion,
        String certificado,
        String codigoCertificado) {

    public ReporteAcademicoResponse {
        condiciones = List.copyOf(condiciones);
        condicionesPendientes = List.copyOf(condicionesPendientes);
    }
}
