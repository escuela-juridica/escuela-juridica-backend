package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record PendienteRevisionRow(Long matriculaId, long cantidad, Instant fechaLimite) {
}
