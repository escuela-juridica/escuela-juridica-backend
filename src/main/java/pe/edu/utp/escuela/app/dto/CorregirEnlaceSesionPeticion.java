package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

/** HU-027 — Corrige el enlace de reunión de una sesión EN_VIVO ya realizada, sin tocar fechas. */
public record CorregirEnlaceSesionPeticion(@NotBlank String enlaceReunion) {
}
