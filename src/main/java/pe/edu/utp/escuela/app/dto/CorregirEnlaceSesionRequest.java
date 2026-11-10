package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CorregirEnlaceSesionRequest(@NotBlank String enlaceReunion) {
}
