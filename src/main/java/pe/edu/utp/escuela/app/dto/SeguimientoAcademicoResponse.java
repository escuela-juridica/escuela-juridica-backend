package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * HU-031 — Vista de solo lectura del desempeño de un alumno en un curso, con lo necesario para
 * explicar por qué no finaliza y para atender excepciones justificadas. {@code asistencia} es
 * {@code null} cuando no aplica (curso virtual, no exigida o sin sesiones elegibles).
 */
public record SeguimientoAcademicoResponse(
        Long matriculaId,
        Long cursoId,
        String curso,
        String modalidad,
        String estadoMatricula,
        String alumno,
        String correo,
        Instant fechaMatricula,
        int progreso,
        List<CondicionAvanceResponse> condiciones,
        boolean cumpleRequisitos,
        String situacion,
        BigDecimal notaFinal,
        Integer asistencia,
        boolean asistenciaExigida,
        List<ModuloSeguimiento> modulos,
        List<ExamenSeguimiento> examenes,
        List<SesionSeguimiento> sesiones,
        CertificadoSeguimiento certificado,
        List<ExcepcionResponse> excepciones) {

    public SeguimientoAcademicoResponse {
        condiciones = List.copyOf(condiciones);
        modulos = List.copyOf(modulos);
        examenes = List.copyOf(examenes);
        sesiones = List.copyOf(sesiones);
        excepciones = List.copyOf(excepciones);
    }

    public record ModuloSeguimiento(Long id, String titulo, List<LeccionSeguimiento> lecciones) {
    }

    /** {@code estado}: COMPLETADA, DISPONIBLE, BLOQUEADA o CANCELADA. {@code forma}: cómo se completó. */
    public record LeccionSeguimiento(Long id, String titulo, String tipo, boolean obligatoria, String estado,
            String forma, Instant completadaEn, String motivoBloqueo) {
    }

    /**
     * {@code estado}: PENDIENTE, EN_CURSO, EN_REVISION, APROBADO o NO_APROBADO. {@code
     * intentosMaximos} es {@code null} si son ilimitados. {@code puedeOtorgarIntento} indica si
     * administración puede conceder ahora un intento adicional; si no, {@code motivoNoPuedeOtorgar}
     * explica por qué.
     */
    public record ExamenSeguimiento(Long id, String titulo, String tipo, String finalidad, String modulo,
            String estado, int intentosUsados, Integer intentosMaximos, BigDecimal mejorNota, boolean aprobado,
            boolean agotadoSinAprobar, boolean puedeOtorgarIntento, String motivoNoPuedeOtorgar,
            List<IntentoResumenResponse> intentos) {
    }

    /** {@code asistencia}: PRESENTE, AUSENTE, JUSTIFICADA, PENDIENTE o SIN_REGISTRO. */
    public record SesionSeguimiento(Long leccionId, Long asistenciaId, String titulo, Instant inicio,
            String estadoSesion, String asistencia, String motivoCorreccion, boolean elegible,
            boolean puedeCorregir) {
    }

    public record CertificadoSeguimiento(String codigo, String estado, Instant emitidoEn) {
    }

    public record ExcepcionResponse(Long id, String tipo, String referencia, String valorAnterior,
            String valorNuevo, String motivo, String realizadoPor, Instant realizadoEn) {
    }
}
