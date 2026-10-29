package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-037 — Excepción económica o de capacidad que la Escuela atiende fuera del procesamiento bancario. Pago,
 * matrícula, acceso y cupo se informan por separado: atender un caso nunca cambia el resultado de Culqi.
 */
public record AtencionPagoResponse(
        Long id,
        String tipo,
        String estado,
        String descripcion,
        String resultado,
        Instant creadoEn,
        Instant atendidoEn,
        String atendidoPor,
        Long matriculaId,
        String alumno,
        String correo,
        Long cursoId,
        String curso,
        PagoInfo pago,
        MatriculaInfo matricula,
        CursoInfo cursoDetalle,
        List<PagoRelacionado> relacionados) {

    public record PagoInfo(Long id, String numeroPedido, String estado, BigDecimal importe, String medio,
            String referencia, Instant resultadoEn, String constancia) {
    }

    /** {@code acceso}: CONCEDIDO solo con matrícula ACTIVA; en cualquier otro estado no hay acceso. */
    public record MatriculaInfo(String estado, String formaIngreso, Instant fechaMatricula, String acceso) {
    }

    public record CursoInfo(String estado, Integer cupoMaximo, long matriculasActivas, LocalDate fechaCierreMatricula,
            boolean sobrecupo) {
    }

    /** Otros pagos del mismo curso con la misma incidencia pendiente (el sobrecupo agrupa varias aprobaciones). */
    public record PagoRelacionado(Long pagoId, String numeroPedido, String alumno, BigDecimal importe, String estado) {
    }
}
