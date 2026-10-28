package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** HU-045 — Opción de los filtros del reporte: un curso en vivo/híbrido ({@code fecha} nula) o una
 * sesión de ese curso ({@code fecha} = inicio de la sesión). */
public record OpcionAsistenciaResponse(Long id, String etiqueta, String detalle, Instant fecha) {
}
