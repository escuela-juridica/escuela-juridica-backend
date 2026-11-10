package pe.edu.utp.escuela.app.dto;

public record PanelCursosResponse(PageResponse<MiCursoResponse> cursos, long enProgreso, long completados) {
}
