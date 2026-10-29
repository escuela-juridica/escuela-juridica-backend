package pe.edu.utp.escuela.app.dto;
import jakarta.validation.constraints.*;
public record RegistroGoogleRequest(
        @NotBlank String referencia,
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        @Size(max = 30) String telefono,
        @Size(max = 30) String documentoIdentidad,
        @AssertTrue(message = "Debes aceptar los términos y la política de privacidad") boolean aceptaTerminos) {
    @Override public String toString() { return "RegistroGoogleRequest[datos sensibles omitidos]"; }
}
