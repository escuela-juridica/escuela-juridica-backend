package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
