package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record AulaResponse(
        Long matriculaId,
        Long cursoId,
        String cursoTitulo,
        String modalidad,
        int porcentaje,
        List<ModuloAulaResponse> modulos,
        Long ultimaLeccionIdValida,
        AvanceCertificadoResponse avanceCertificado,
        List<ExamenAulaResponse> examenesFinales,
        String imagenPortadaUrl,
        java.math.BigDecimal horasAcademicas,
        boolean cursoCancelado,
        String motivoCancelacionCurso) {

    public AulaResponse {
        modulos = List.copyOf(modulos);
        examenesFinales = List.copyOf(examenesFinales);
    }
}
