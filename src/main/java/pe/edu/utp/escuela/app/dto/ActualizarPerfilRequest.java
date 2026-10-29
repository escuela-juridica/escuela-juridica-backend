package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActualizarPerfilRequest(
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        @Pattern(regexp = "\\d{6,9}", message = "El teléfono debe tener entre 6 y 9 dígitos")
        String telefono,
        @Pattern(regexp = "\\d{8}", message = "El documento debe tener 8 dígitos")
        String documentoIdentidad) {
}
