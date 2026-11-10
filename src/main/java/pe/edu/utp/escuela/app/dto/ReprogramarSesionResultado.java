package pe.edu.utp.escuela.app.dto;

import java.time.LocalDate;

public record ReprogramarSesionResultado(
        boolean aplicado,
        boolean requiereAmpliarFinDeCurso,
        LocalDate fechaFinCursoSugerida) {
}
