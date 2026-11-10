package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

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
