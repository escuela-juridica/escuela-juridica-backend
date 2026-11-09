package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarSesionRequest(@NotBlank String motivo) {
}
