package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record FiltrosCursoResponse(
        List<OpcionFiltroResponse> tipos,
        List<OpcionFiltroResponse> categorias) {

    public FiltrosCursoResponse {
        tipos = List.copyOf(tipos);
        categorias = List.copyOf(categorias);
    }
}
