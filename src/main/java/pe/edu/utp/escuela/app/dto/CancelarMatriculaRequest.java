package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarMatriculaRequest(@NotBlank String motivo) {
}
