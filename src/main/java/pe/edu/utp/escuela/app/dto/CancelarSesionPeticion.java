package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

/** HU-027 — Cancela definitivamente una sesión EN_VIVO futura; no es reversible desde este endpoint. */
public record CancelarSesionPeticion(@NotBlank String motivo) {
}
