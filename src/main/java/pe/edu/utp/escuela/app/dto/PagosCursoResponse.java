package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record PagosCursoResponse(
        String estadoMatricula,
        String pendiente,
        List<PagoDetalleResponse> intentos) {
}
