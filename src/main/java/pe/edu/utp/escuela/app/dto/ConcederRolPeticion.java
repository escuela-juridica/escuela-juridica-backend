package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotNull;

public record ConcederRolPeticion(@NotNull RolUsuarioAdmin rol) {
}
