package pe.edu.utp.escuela.app.dto;

public record AsistenciaSesionResponse(
        Long asistenciaId,
        Long matriculaId,
        String alumnoNombre,
        String estado) {
}
