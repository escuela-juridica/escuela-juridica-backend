package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record ReporteMatriculaResponse(
        Long matriculaId, String alumno, String correo, String curso, String modalidad,
        Instant fechaMatricula, Instant fechaActivacion, String estadoMatricula, String formaIngreso,
        String situacionAcademica, String estadoCertificado) {}
