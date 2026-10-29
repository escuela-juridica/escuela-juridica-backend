package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * HU-040 — Reenvío de la misma respuesta. {@code correo} es opcional: solo se indica para corregir el destino de un
 * caso que sigue pendiente; el intento queda registrado con el correo usado.
 */
public record ReenviarRespuestaRequest(
        @Size(max = 254, message = "El correo no puede superar 254 caracteres.")
        @Email(message = "Ingresa un correo válido.")
        String correo) {
}
