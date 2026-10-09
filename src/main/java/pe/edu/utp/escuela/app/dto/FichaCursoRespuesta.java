package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FichaCursoRespuesta(
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
        List<DocenteFichaRespuesta> docentes,
        List<ModuloFichaRespuesta> modulos,
        EstadoComercialRespuesta estadoComercial) {

    public FichaCursoRespuesta {
        beneficios = List.copyOf(beneficios);
        docentes = List.copyOf(docentes);
        modulos = List.copyOf(modulos);
    }
}
