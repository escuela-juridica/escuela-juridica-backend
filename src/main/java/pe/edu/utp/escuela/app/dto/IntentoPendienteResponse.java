package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-030 — Una fila de la bandeja de revisión. {@code plazo}: VENCIDO (la fecha límite ya pasó),
 * PROXIMO (vence en menos de 24 horas) o EN_PLAZO. {@code minutosRestantes} es negativo cuando
 * ya está vencido. Vencer no califica ni cierra nada: solo señala demora.
 */
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
