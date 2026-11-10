package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    public record FirmanteVista(String nombre, String cargo) {
    }

    public record CertificadoResumen(
            String codigo,
            String estado,
            String nivel,
            String tipoEmision,
            Instant emitidoEn,
            String motivoEmisionManual) {
    }
}
