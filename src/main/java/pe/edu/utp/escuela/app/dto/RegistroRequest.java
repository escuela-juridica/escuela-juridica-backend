package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.*;

public record RegistroRequest(
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        @NotBlank @Email @Size(max = 254) String correo,
        @Size(max = 30) String telefono,
        @Size(max = 30) String documentoIdentidad,
        @NotBlank String contrasena,
        @NotBlank String confirmarContrasena,
        @AssertTrue(message = "Debes aceptar los términos y la política de privacidad") boolean aceptaTerminos,
        @NotBlank String evidenciaAntiRobot) {
    @Override public String toString() { return "RegistroRequest[datos sensibles omitidos]"; }
}
