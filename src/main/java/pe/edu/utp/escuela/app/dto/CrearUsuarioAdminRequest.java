package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CrearUsuarioAdminRequest(
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        @NotBlank @Email @Size(max = 254) String correo,
        @Size(max = 30) String telefono,
        @Size(max = 30) String documentoIdentidad,
        @NotEmpty List<RolUsuarioAdmin> roles) {
}
