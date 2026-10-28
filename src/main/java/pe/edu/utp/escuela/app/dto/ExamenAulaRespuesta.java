package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HU-025 / HU-028 — Examen tal como lo ve el alumno en el aula.
 *
 * <p>{@code estado}: PENDIENTE (sin intentos), EN_CURSO (intento abierto), EN_REVISION (respuesta
 * abierta sin calificar), APROBADO o NO_APROBADO. {@code habilitado} es falso mientras el alumno no
 * cumple el avance (o la fecha) que exige el examen; {@code motivoNoHabilitado} dice cuál.
 * {@code intentosMaximos} es {@code null} cuando los intentos son ilimitados.
 */
public record ExamenAulaRespuesta(
        Long id,
        String titulo,
        String tipo,
        String finalidad,
        boolean bloqueaSiguienteModulo,
        boolean aprobado,
        BigDecimal mejorNota,
        String estado,
        boolean habilitado,
        String motivoNoHabilitado,
        int intentosUsados,
        Integer intentosMaximos,
        Instant fechaLimiteRevision) {
}
