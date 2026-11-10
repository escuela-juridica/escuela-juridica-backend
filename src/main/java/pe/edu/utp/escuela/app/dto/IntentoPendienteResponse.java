package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record IntentoPendienteResponse(
        Long intentoId,
        Long matriculaId,
        String alumno,
        String correo,
        Long cursoId,
        String curso,
        Long examenId,
        String examen,
        int numeroIntento,
        Instant enviadoEn,
        Instant fechaLimite,
        String plazo,
        long minutosRestantes,
        long respuestasPendientes) {
}
