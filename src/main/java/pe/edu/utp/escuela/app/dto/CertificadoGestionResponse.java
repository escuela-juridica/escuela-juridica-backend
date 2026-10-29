package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-035/HU-036 — Una fila de la gestión administrativa de certificados: una matrícula con la situación
 * de su certificado. {@code estadoCertificado} es SIN_CERTIFICADO, VIGENTE o ANULADO.
 */
public record CertificadoGestionResponse(
        Long matriculaId,
        Long certificadoId,
        String alumno,
        String correo,
        Long cursoId,
        String curso,
        String estadoMatricula,
        String estadoCertificado,
        String codigo,
        String nivel,
        String tipoEmision,
        Instant emitidoEn,
        boolean puedeEmitirManual) {
}
