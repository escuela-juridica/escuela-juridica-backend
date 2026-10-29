package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-035 — Lo que administración revisa antes de emitir un certificado excepcional: avance real,
 * nota y nivel que se congelarían, datos confirmados por el alumno y, si existe, el certificado.
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
        List<CondicionAvanceResponse> condiciones,
        boolean cumpleTodo,
        String frase,
        CertificadoResumen certificado,
        String bloqueo) {

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
