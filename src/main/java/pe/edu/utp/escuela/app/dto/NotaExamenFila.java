package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;

/** HU-025 — Mejor nota calificada de un alumno en un examen. */
public record NotaExamenFila(Long examenId, BigDecimal nota) {
}
