package pe.edu.utp.escuela.app.adminusuario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambiarActivoPeticion(@NotNull Boolean activo, @Size(max = 300) String motivo) {
}
