package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    public record PuntoMonto(String periodo, String etiqueta, BigDecimal monto) {
    }
}
