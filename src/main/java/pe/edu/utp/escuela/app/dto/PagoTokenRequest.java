package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-047 — Token (tarjeta o Yape) que el checkout de Culqi entregó al navegador. */
public record PagoTokenRequest(@NotBlank @Size(max = 120) String tokenId) {
}
