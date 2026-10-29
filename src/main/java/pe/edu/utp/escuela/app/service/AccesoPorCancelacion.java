package pe.edu.utp.escuela.app.service;

import java.time.Instant;
import java.time.ZoneId;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Matricula;

/**
 * HU-038 — Quién conserva la consulta de un curso CANCELADO. Solo quien ya tenía contenido disponible al momento de
 * cancelar: matrícula no cancelada individualmente, cuenta habilitada, acceso vigente y curso ya iniciado. La
 * cancelación nunca habilita contenido nuevo: si el curso no había comenzado (o el acceso ya había vencido) no hay
 * nada que consultar. Es una sola regla compartida por el aula, los materiales y los exámenes para que no diverja.
 */
final class AccesoPorCancelacion {

    private AccesoPorCancelacion() {
    }

    static boolean cursoCancelado(Matricula m) {
        return "CANCELADO".equals(m.getCurso().getEstadoCurso().getCodigo());
    }

    static boolean permiteLectura(Matricula m, boolean cuentaHabilitada, ZoneId zona) {
        if (!cursoCancelado(m) || "CANCELADA".equals(m.getEstado()) || !cuentaHabilitada) {
            return false;
        }
        Curso curso = m.getCurso();
        Instant corte = curso.getCanceladoEn() != null ? curso.getCanceladoEn() : Instant.now();
        boolean vigenteAlCancelar = m.getFechaVencimiento() == null || !corte.isAfter(m.getFechaVencimiento());
        boolean habiaIniciado = curso.getFechaInicio() == null
                || !corte.atZone(zona).toLocalDate().isBefore(curso.getFechaInicio());
        return vigenteAlCancelar && habiaIniciado;
    }

    static final String MENSAJE_SIN_CONTENIDO =
            "El curso fue cancelado antes de que hubiera contenido disponible para ti.";
}
