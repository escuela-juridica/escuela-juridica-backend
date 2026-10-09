package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ActualizarConfiguracionPeticion(@NotBlank String valor, String descripcion) {
}
