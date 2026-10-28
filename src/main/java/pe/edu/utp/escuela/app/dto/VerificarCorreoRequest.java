package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerificarCorreoRequest(
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "El código debe tener seis dígitos") String codigo) {
}
