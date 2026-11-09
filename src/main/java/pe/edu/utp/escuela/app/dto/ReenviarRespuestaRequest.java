package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ReenviarRespuestaRequest(
        @Size(max = 254, message = "El correo no puede superar 254 caracteres.")
        @Email(message = "Ingresa un correo válido.")
        String correo) {
}
