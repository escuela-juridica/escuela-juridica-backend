package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record ReporteCertificadoResponse(
        Long certificadoId,
        String codigo,
        String alumno,
        Long cursoId,
        String curso,
        String nivel,
        String entidad,
        Instant fechaFinalizacion,
        Instant fechaEmision,
        String estado,
        String forma) {
}
