package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CursoTarjetaRow(
        Long cursoId,
        String urlAmigable,
        String titulo,
        String descripcion,
        String imagenPortadaUrl,
        String modalidad,
        String tipoVenta,
        boolean destacado,
        BigDecimal precioRegular,
        BigDecimal precioPromocional,
        Instant promocionInicioEn,
        Instant promocionFinEn,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        LocalDate fechaCierreMatricula,
        Integer cupoMaximo,
        BigDecimal horasAcademicas,
        String tipoCursoCodigo,
        String tipoCursoNombre,
        String categoriaCodigo,
        String categoriaNombre,
        String estadoCursoCodigo) {
}
