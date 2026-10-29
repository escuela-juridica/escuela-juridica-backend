package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * HU-047 — Solo con Culqi simulado: reproduce el aviso que Culqi enviaría. {@code repetirUltimo} reenvía el mismo
 * evento para comprobar que una confirmación repetida no duplica ningún efecto.
 */
public record PagoSimulacionRequest(
        @NotBlank @Pattern(regexp = "APROBADO|RECHAZADO|ERROR|EXPIRADO") String resultado,
        boolean repetirUltimo) {
}
