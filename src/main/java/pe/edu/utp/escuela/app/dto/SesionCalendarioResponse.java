package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-026: una sesión en vivo dentro del calendario del alumno.
 *
 * <p>{@code enlaceReunion} solo viene informado cuando {@code estadoSesion = "EN_VENTANA"}; en
 * cualquier otro estado ({@code FUTURA}, {@code REALIZADA}, {@code CANCELADA}) va {@code null}
 * aunque el dato exista en la base, porque el enlace real no se expone fuera de la ventana.</p>
 */
public record SesionCalendarioResponse(
        Long leccionId,
        Long cursoId,
        String cursoTitulo,
        String titulo,
        Instant fechaHoraInicio,
        Instant fechaHoraFin,
        String estadoSesion,
        String enlaceReunion,
        boolean grabacionDisponible) {
}
