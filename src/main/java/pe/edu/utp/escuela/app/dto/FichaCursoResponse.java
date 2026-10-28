package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FichaCursoResponse(
        Long cursoId,
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
        String entidadCertificadoraNombre,
        String entidadCertificadoraLogoUrl,
        BigDecimal horasAcademicas,
        List<String> beneficios,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        List<DocenteFichaResponse> docentes,
        List<ModuloFichaResponse> modulos,
        EstadoComercialResponse estadoComercial) {

    public FichaCursoResponse {
        beneficios = List.copyOf(beneficios);
        docentes = List.copyOf(docentes);
        modulos = List.copyOf(modulos);
    }
}
