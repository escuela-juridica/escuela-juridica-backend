package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ActualizarConfiguracionRequest(@NotBlank String valor, String descripcion) {
}
