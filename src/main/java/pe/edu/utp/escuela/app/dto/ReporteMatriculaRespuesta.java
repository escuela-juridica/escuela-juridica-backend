package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** Una fila por matricula; separa acceso, origen y situacion academica para HU-041. */
public record ReporteMatriculaRespuesta(
        Long matriculaId, String alumno, String correo, String curso, String modalidad,
        Instant fechaMatricula, Instant fechaActivacion, String estadoMatricula, String formaIngreso,
        String situacionAcademica) {}
