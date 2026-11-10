package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccesoGoogleRequest(@NotBlank @Size(max = 8192) String credencial) {
    @Override public String toString() { return "AccesoGoogleRequest[credencial omitida]"; }
}
