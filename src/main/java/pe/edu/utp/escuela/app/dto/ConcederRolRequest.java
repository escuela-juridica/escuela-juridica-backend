package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotNull;

public record ConcederRolRequest(@NotNull RolUsuarioAdmin rol) {
}
