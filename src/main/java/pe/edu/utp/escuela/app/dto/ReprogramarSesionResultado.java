package pe.edu.utp.escuela.app.dto;

import java.time.LocalDate;

/**
 * HU-027 — Si {@code aplicado} es falso y {@code requiereAmpliarFinDeCurso} es verdadero, no se
 * guardó ningún cambio todavía: el frontend debe ofrecer ampliar la fecha de fin del curso (ver
 * {@code fechaFinCursoSugerida}, solo informativa) y volver a llamar a este mismo endpoint.
 */
public record ReprogramarSesionResultado(
        boolean aplicado,
        boolean requiereAmpliarFinDeCurso,
        LocalDate fechaFinCursoSugerida) {
}
