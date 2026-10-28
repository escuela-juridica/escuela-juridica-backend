package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** HU-045 — Fila cruda de la consulta del reporte (un alumno en una sesión); el porcentaje
 * acumulado y el cumplimiento se agregan después, por matrícula, en el servicio. */
public record ReporteAsistenciaFila(
        Long matriculaId,
        Long cursoId,
        String curso,
        String modalidad,
        Long leccionId,
        String sesion,
        Instant fechaSesion,
        String alumno,
        String correo,
        String asistencia,
        String origen,
        String motivoCorreccion) {
}
