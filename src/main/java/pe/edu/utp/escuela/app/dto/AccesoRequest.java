package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AccesoRequest(
        @NotBlank @Email String correo,
        @NotBlank String contrasena) {
}
