package pe.edu.utp.escuela.app.dto;

import java.util.List;

/** HU-022 — Temario completo del aula de un alumno para un curso, con el punto donde debe
 * posicionarse "Continuar". */
public record AulaRespuesta(
        Long matriculaId,
        Long cursoId,
        String cursoTitulo,
        String modalidad,
        int porcentaje,
        List<ModuloAulaRespuesta> modulos,
        Long ultimaLeccionIdValida,
        AvanceCertificadoRespuesta avanceCertificado) {

    public AulaRespuesta {
        modulos = List.copyOf(modulos);
    }
}
