package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** HU-030 — Un intento pendiente de revisión, tal como sale de la consulta de la bandeja. */
public record BandejaRevisionRow(
        Long intentoId,
        Long matriculaId,
        String alumno,
        String correo,
        Long cursoId,
        String curso,
        Long examenId,
        String examen,
        Integer numeroIntento,
        Instant enviadoEn,
        Instant fechaLimite,
        long respuestasPendientes) {
}
