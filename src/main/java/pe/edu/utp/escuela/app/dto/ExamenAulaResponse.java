package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ExamenAulaResponse(
        Long id,
        String titulo,
        String tipo,
        String finalidad,
        boolean bloqueaSiguienteModulo,
        boolean aprobado,
        BigDecimal mejorNota,
        String estado,
        boolean habilitado,
        String motivoNoHabilitado,
        int intentosUsados,
        Integer intentosMaximos,
        Instant fechaLimiteRevision) {
}
