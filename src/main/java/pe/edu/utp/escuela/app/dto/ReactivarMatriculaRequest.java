package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ReactivarMatriculaRequest(@NotBlank String motivo) {
}