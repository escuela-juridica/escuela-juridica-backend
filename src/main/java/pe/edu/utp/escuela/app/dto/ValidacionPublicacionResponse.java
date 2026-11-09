package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record ValidacionPublicacionResponse(
        boolean puedePublicarse,
        List<ErrorValidacionCurso> hallazgos,
        boolean publicacionRealizada,
        String estadoCodigo) {
}
