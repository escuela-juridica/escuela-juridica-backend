package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** Proyección de una fila de usuario_rol con el código del rol ya resuelto. */
public record UsuarioRolFila(
        Long usuarioId,
        String rolCodigo,
        boolean principal,
        Long asignadoPorUsuarioId,
        Instant asignadoEn) {
}
