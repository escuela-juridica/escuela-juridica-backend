package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;

/** HU-025 — Examen de un módulo tal como lo ve el alumno en el aula. Rendirlo es Épica 5. */
public record ExamenAulaRespuesta(
        Long id,
        String titulo,
        String tipo,
        boolean bloqueaSiguienteModulo,
        boolean aprobado,
        BigDecimal mejorNota) {
}
