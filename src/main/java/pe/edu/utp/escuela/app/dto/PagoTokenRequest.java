package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PagoTokenRequest(@NotBlank @Size(max = 120) String tokenId) {
}
