package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        Long matriculaId,
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

    public record Cambio(Long id, String accion, String datosAnteriores, String datosNuevos, String motivo,
            String realizadoPor, Instant realizadoEn) {
    }
}
