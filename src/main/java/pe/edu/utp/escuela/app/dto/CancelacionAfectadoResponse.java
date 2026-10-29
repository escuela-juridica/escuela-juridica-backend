package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-038 — Un alumno con acceso previo afectado por la cancelación y el aviso consolidado que se le envía.
 * {@code estadoAviso}: PENDIENTE, ENVIADO o ERROR; un fallo se puede reintentar sin duplicar el aviso.
 */
public record CancelacionAfectadoResponse(
        Long usuarioId,
        String alumno,
        String correo,
        boolean finalizado,
        String estadoAviso,
        int intentos,
        String ultimoError,
        Instant enviadoEn) {
}
