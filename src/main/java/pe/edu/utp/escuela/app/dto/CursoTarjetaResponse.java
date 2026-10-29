package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record CursoTarjetaResponse(
        String urlAmigable,
        String titulo,
        String descripcion,
        String imagenPortadaUrl,
        String modalidad,
        String tipoVenta,
        String tipoCursoCodigo,
        String tipoCursoNombre,
        String categoriaCodigo,
        String categoriaNombre,
        boolean destacado,
        BigDecimal horasAcademicas,
        List<DocenteBreveResponse> docentes,
        EstadoComercialResponse estadoComercial) {

    public CursoTarjetaResponse {
        docentes = List.copyOf(docentes);
    }
}
