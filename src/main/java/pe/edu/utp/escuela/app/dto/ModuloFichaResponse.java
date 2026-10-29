package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record ModuloFichaResponse(
        Long moduloId,
        String titulo,
        String descripcion,
        Integer orden,
        List<LeccionFichaResponse> lecciones) {

    public ModuloFichaResponse {
        lecciones = List.copyOf(lecciones);
    }
}
