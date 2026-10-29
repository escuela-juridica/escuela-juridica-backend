package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-032 — Situación del certificado de un alumno en un curso. {@code estado}: PENDIENTE_REQUISITOS,
 * PROGRAMADO, PENDIENTE_DATOS, LISTO_PARA_EMITIR y, una vez emitido, VIGENTE o ANULADO. {@code
 * condiciones} son solo las activas del curso con su valor real y su meta. {@code notaFinal},
 * {@code nivelProyectado} y {@code entidadProyectada} son una proyección con lo que lleva hasta
 * hoy (el nivel final se congela al emitir); {@code null} si el curso no exige exámenes.
 */
public record CertificacionResponse(
        Long cursoId,
        String curso,
        String estado,
        String mensaje,
        List<CondicionAvanceResponse> condiciones,
        String fraseAccionable,
        boolean exigeExamenes,
        BigDecimal notaFinal,
        String nivelProyectado,
        String entidadProyectada,
        Instant fechaFinalizacion,
        LocalDate fechaBaseEmision,
        Instant fechaEmisionProgramada,
        int diasEspera,
        boolean emisionAutomatica,
        DatosCertificado datos,
        boolean puedeConfirmarDatos,
        boolean puedeGenerarAhora,
        CertificadoEmitido certificado) {

    public CertificacionResponse {
        condiciones = List.copyOf(condiciones);
    }

    /** Lo que el alumno confirmó (o lo que se le sugiere si aún no confirmó). */
    public record DatosCertificado(String nombres, String apellidoPaterno, String apellidoMaterno,
            boolean confirmados, Instant confirmadosEn) {
    }

    public record CertificadoEmitido(String codigo, String estado, String nivel, BigDecimal nota, Instant emitidoEn,
            String nombresImpresos, String entidad, String descargaUrl) {
    }
}
