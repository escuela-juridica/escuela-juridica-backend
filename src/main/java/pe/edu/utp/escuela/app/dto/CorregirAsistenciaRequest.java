package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

/** HU-027 — Corrige el estado de una asistencia ya registrada, dejando el motivo del ajuste. */
public record CorregirAsistenciaRequest(
        @NotBlank String nuevoEstado,
        @NotBlank String motivoCorreccion) {
}
