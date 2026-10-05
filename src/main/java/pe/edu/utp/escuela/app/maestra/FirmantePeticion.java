package pe.edu.utp.escuela.app.maestra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirmantePeticion(
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        @NotBlank @Size(max = 180) String cargoFirma,
        String imagenFirmaUrl) {
}
