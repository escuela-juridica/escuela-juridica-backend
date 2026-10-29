package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReenviarCodigoRequest(
        @NotBlank @Email @Size(max = 254) String correo) {
}
