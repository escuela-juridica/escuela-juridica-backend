package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-035 — Lo que administración revisa antes de emitir un certificado excepcional: avance real,
 * nota y nivel que se congelarían, datos confirmados por el alumno y, si existe, el certificado.
 * Incluye también lo que se imprimiría o ya se imprimió: horas, tipo de curso, periodo y firmantes; {@code fechaFinCurso}
 * viene vacía cuando el fin lo marca la finalización del alumno.
 * {@code bloqueo} explica en palabras por qué no se puede emitir; vacío cuando sí se puede.
 */
public record EmisionManualSituacionResponse(
        Long matriculaId,
        String alumno,
        String correo,
        String curso,
        String modalidad,
        String estadoMatricula,
        Instant fechaMatricula,
        Instant fechaFinalizacion,
        LocalDate fechaMinimaFinalizacion,
        LocalDate fechaMaximaFinalizacion,
        boolean datosConfirmados,
        String nombresConfirmados,
        BigDecimal notaFinal,
        String nivel,
        String entidad,
        BigDecimal horas,
        String tipoCurso,
        LocalDate fechaInicio,
        LocalDate fechaFinCurso,
        List<FirmanteVista> firmantes,
        List<CondicionAvanceResponse> condiciones,
        boolean cumpleTodo,
        String frase,
        CertificadoResumen certificado,
        String bloqueo) {

    /** Firmante tal como figura (o figuraría) en el certificado. */
    public record FirmanteVista(String nombre, String cargo) {
    }

    /** Certificado ya emitido para esa matrícula. */
    public record CertificadoResumen(
            String codigo,
            String estado,
            String nivel,
            String tipoEmision,
            Instant emitidoEn,
            String motivoEmisionManual) {
    }
}
