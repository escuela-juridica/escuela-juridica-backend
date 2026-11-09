package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record UsuarioRolRow(
        Long usuarioId,
        String rolCodigo,
        boolean principal,
        Long asignadoPorUsuarioId,
        Instant asignadoEn) {
}
