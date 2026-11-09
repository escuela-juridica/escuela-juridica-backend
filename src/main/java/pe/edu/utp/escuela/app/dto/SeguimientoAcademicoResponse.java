package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    public record LeccionSeguimiento(Long id, String titulo, String tipo, boolean obligatoria, String estado,
            String forma, Instant completadaEn, String motivoBloqueo) {
    }

    public record ExamenSeguimiento(Long id, String titulo, String tipo, String finalidad, String modulo,
            String estado, int intentosUsados, Integer intentosMaximos, BigDecimal mejorNota, boolean aprobado,
            boolean agotadoSinAprobar, boolean puedeOtorgarIntento, String motivoNoPuedeOtorgar,
            List<IntentoResumenResponse> intentos) {
    }

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
