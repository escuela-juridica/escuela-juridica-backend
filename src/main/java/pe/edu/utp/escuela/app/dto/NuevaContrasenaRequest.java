package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record NuevaContrasenaRequest(
        @NotBlank String contrasena,
        @NotBlank String confirmacion) {
}
