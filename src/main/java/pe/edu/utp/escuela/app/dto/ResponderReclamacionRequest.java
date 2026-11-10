package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponderReclamacionRequest(
        @NotBlank(message = "Escribe la respuesta para la persona reclamante.")
        @Size(min = 20, max = 5000, message = "La respuesta debe tener entre 20 y 5000 caracteres.")
        String respuesta) {
}
