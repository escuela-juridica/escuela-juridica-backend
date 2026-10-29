package pe.edu.utp.escuela.app.dto;

import java.util.List;

/** HU-047 — Historial de intentos del alumno en un curso y si hay uno en proceso que puede continuar. */
public record PagosCursoResponse(
        String estadoMatricula,
        String pendiente,
        List<PagoDetalleResponse> intentos) {
}
