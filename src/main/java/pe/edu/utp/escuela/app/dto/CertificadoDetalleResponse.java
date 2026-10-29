package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * HU-036 — Certificado tal como lo ve la administración para corregirlo o anularlo: datos documentales vigentes, la
 * nota y el nivel congelados (solo lectura), los firmantes impresos y el historial completo de cambios. El motivo de
 * una anulación es privado de la administración; la verificación pública nunca lo muestra.
 */
public record CertificadoDetalleResponse(
        Long certificadoId,
        String codigo,
        String estado,
        String tipoEmision,
        String nivel,
        BigDecimal nota,
        String alumno,
        String correo,
        Long cursoId,
        String nombres,
        String titulo,
        String tipoCurso,
        BigDecimal horas,
        String entidad,
        Instant emitidoEn,
        Instant anuladoEn,
        String motivoAnulacion,
        boolean puedeModificar,
        List<FirmanteImpreso> firmantes,
        List<FirmanteDisponible> firmantesDisponibles,
        List<Cambio> historial) {

    public record FirmanteImpreso(Long firmanteId, String nombre, String cargo) {
    }

    public record FirmanteDisponible(Long firmanteId, String nombre, String cargo) {
    }

    /** Una versión conservada: qué se reemplazó, qué quedó, por qué, quién y cuándo. */
    public record Cambio(Long id, String accion, String datosAnteriores, String datosNuevos, String motivo,
            String realizadoPor, Instant realizadoEn) {
    }
}
