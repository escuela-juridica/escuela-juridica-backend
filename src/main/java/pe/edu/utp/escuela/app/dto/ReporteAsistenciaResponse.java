package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record ReporteAsistenciaResponse(
        Long matriculaId,
        Long cursoId,
        String curso,
        String modalidad,
        Long leccionId,
        String sesion,
        String modulo,
        Instant fechaSesion,
        String alumno,
        String correo,
        String asistencia,
        String origen,
        String motivoCorreccion,
        Integer porcentajeAcumulado,
        String cumplimiento) {
}
