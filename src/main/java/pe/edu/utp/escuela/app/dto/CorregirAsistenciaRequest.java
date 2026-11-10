package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CorregirAsistenciaRequest(
        @NotBlank String nuevoEstado,
        @NotBlank String motivoCorreccion) {
}
