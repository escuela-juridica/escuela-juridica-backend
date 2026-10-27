package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** HU-022 — Estado de una lección dentro del aula del alumno.
 *
 * {@code estado} es "COMPLETADA", "DISPONIBLE" o "BLOQUEADA"; {@code motivoBloqueo} solo viene
 * relleno cuando está BLOQUEADA. Los campos de sesión ({@code fechaHoraInicio}, {@code
 * fechaHoraFin}, {@code estadoSesion}) solo aplican cuando {@code tipo} es EN_VIVO; en cualquier
 * otro caso llegan en null. {@code estadoSesion} es "FUTURA", "EN_VENTANA", "REALIZADA" o
 * "CANCELADA". */
public record LeccionAulaRespuesta(
        Long id,
        String titulo,
        String tipo,
        int orden,
        boolean esObligatoria,
        String estado,
        String motivoBloqueo,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String estadoSesion,
        boolean grabacionDisponible) {
}
