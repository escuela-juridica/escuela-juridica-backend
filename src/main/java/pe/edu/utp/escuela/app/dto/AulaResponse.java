package pe.edu.utp.escuela.app.dto;

import java.util.List;

/** HU-022 — Temario completo del aula de un alumno para un curso, con el punto donde debe
 * posicionarse "Continuar". HU-028: {@code examenesFinales} son los exámenes sin módulo. */
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
