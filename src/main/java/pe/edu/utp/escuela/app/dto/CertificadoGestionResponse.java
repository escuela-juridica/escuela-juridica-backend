package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

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
        boolean puedeEmitirManual,
        int avance,
        boolean cumpleRequisitos,
        Instant fechaMatricula) {
}
