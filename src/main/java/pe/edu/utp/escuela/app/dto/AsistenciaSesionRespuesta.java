package pe.edu.utp.escuela.app.dto;

/** HU-027 — Una fila de asistencia de una sesión realizada, para el modal "Corregir sesión". */
public record AsistenciaSesionRespuesta(
        Long asistenciaId,
        Long matriculaId,
        String alumnoNombre,
        String estado) {
}
