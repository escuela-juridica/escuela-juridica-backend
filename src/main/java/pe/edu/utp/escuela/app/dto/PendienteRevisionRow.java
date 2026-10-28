package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** HU-043 — Respuestas abiertas sin revisar de una matrícula y la fecha límite más próxima. */
public record PendienteRevisionRow(Long matriculaId, long cantidad, Instant fechaLimite) {
}
