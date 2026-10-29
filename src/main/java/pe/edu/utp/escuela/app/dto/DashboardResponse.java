package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-046 — Datos de los cuatro gráficos del dashboard administrativo, calculados con el mismo rango, curso y
 * modalidad. Solo contiene lo que se grafica: ningún indicador extra, tarea ni dato personal.
 * {@code granularidad}: DIA (rangos de hasta 31 días) o MES.
 */
public record DashboardResponse(
        LocalDate desde,
        LocalDate hasta,
        String granularidad,
        List<Punto> matriculasPorPeriodo,
        List<Barra> matriculasPorCurso,
        List<Barra> formaIngreso,
        List<PuntoMonto> pagosPorPeriodo) {

    public record Punto(String periodo, String etiqueta, long valor) {
    }

    public record Barra(String clave, String etiqueta, long valor) {
    }

    /** Importe confirmado (S/) de un periodo. */
    public record PuntoMonto(String periodo, String etiqueta, BigDecimal monto) {
    }
}
