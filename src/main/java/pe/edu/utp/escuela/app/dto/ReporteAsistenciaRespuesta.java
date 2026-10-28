package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-045 — Una fila del reporte de asistencia: un alumno en una sesión en vivo.
 *
 * <p>{@code asistencia}: PRESENTE, AUSENTE, JUSTIFICADA o PENDIENTE (aún sin registro). {@code
 * origen}: AUTOMATICA (abrió el enlace desde ESEJUR dentro de la ventana, o el cierre de sesión
 * lo marcó ausente), CORRECCION_ADMINISTRATIVA, o {@code null} si no hay registro. {@code
 * porcentajeAcumulado} es {@code null} cuando el alumno no tiene sesiones elegibles ("No aplica",
 * nunca un número engañoso). {@code cumplimiento}: CUMPLE o NO_CUMPLE solo cuando el curso exige
 * asistencia mínima; {@code null} si no es una condición del curso.
 */
public record ReporteAsistenciaRespuesta(
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
        String motivoCorreccion,
        Integer porcentajeAcumulado,
        String cumplimiento) {
}
