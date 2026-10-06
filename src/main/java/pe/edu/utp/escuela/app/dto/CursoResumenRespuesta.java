package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CursoResumenRespuesta(
        Long id,
        String urlAmigable,
        String titulo,
        String estadoCodigo,
        String estadoNombre,
        String modalidad,
        String tipoVenta,
        String tipoCursoNombre,
        String categoriaTematicaNombre,
        BigDecimal precioRegular,
        boolean publicado,
        Instant creadoEn) {
}
