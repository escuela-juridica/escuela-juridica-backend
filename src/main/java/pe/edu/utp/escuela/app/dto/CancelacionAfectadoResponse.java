package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

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
