package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record VistaPreviaResponse(
        Long leccionId,
        List<RecursoVistaPreviaResponse> materiales) {

    public VistaPreviaResponse {
        materiales = List.copyOf(materiales);
    }
}
