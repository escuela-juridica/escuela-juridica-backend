package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-037 — Qué atención brindó la Escuela fuera de ESEJUR (comunicación, devolución por su canal, etc.). */
public record AtenderAtencionPagoRequest(
        @NotBlank(message = "Describe la atención brindada.")
        @Size(min = 10, max = 2000, message = "Describe la atención con entre 10 y 2000 caracteres.")
        String resultado) {
}
