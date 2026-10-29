package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ReportePagoResponse;
import pe.edu.utp.escuela.app.dto.ReportePagoTotalResponse;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.repository.PagoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-042 — Reporte de pagos registrados (administrador). El detalle, el total y la exportación a Excel salen de la
 * misma consulta y los mismos filtros. Solo lee: nunca recalcula ni cambia un pago. El total suma únicamente los
 * pagos confirmados (APROBADO y REGISTRADO_MANUAL con monto mayor que cero).
 */
@Service
@RequiredArgsConstructor
public class ReportePagoService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "APROBADO", "RECHAZADO", "ERROR", "EXPIRADO",
            "REGISTRADO_MANUAL", "EXONERADO");
    private static final Set<String> ORIGENES = Set.of("EN_LINEA", "MANUAL", "EXONERADO");

    private final PagoRepository pagos;
    private final CurrentUserService actual;

    /** Filtros del reporte (todos opcionales); una cadena vacía significa sin filtro. */
    public record Filtro(String texto, Long cursoId, String estado, String medio, String origen, LocalDate desde,
            LocalDate hasta) {
    }

    @Transactional(readOnly = true)
    public Page<ReportePagoResponse> paginado(Filtro filtro, Pageable pageable) {
        Consulta q = preparar(filtro);
        return pagos.buscarReporte(q.texto, filtro.cursoId(), q.estado, q.medio, q.origen, q.desde, q.hasta, pageable)
                .map(ReportePagoService::fila);
    }

    @Transactional(readOnly = true)
    public List<ReportePagoResponse> completo(Filtro filtro) {
        Consulta q = preparar(filtro);
        return pagos.exportarReporte(q.texto, filtro.cursoId(), q.estado, q.medio, q.origen, q.desde, q.hasta)
                .stream().map(ReportePagoService::fila).toList();
    }

    @Transactional(readOnly = true)
    public ReportePagoTotalResponse total(Filtro filtro) {
        Consulta q = preparar(filtro);
        BigDecimal suma = pagos.sumarConfirmados(q.texto, filtro.cursoId(), q.estado, q.medio, q.origen, q.desde, q.hasta);
        long cantidad = pagos.contarConfirmados(q.texto, filtro.cursoId(), q.estado, q.medio, q.origen, q.desde, q.hasta);
        return new ReportePagoTotalResponse(suma == null ? BigDecimal.ZERO.setScale(2) : suma, cantidad, "PEN");
    }

    /** Medios registrados (tarjeta, Yape, transferencia…), para el filtro. */
    @Transactional(readOnly = true)
    public List<String> medios() {
        exigirAdministrador();
        return pagos.mediosRegistrados();
    }

    private record Consulta(String texto, String estado, String medio, String origen, Instant desde, Instant hasta) {
    }

    private Consulta preparar(Filtro filtro) {
        exigirAdministrador();
        String estado = mayusculas(filtro.estado());
        String origen = mayusculas(filtro.origen());
        if (!estado.isEmpty() && !ESTADOS.contains(estado)) {
            throw new BusinessValidationException("El resultado del pago no es válido.");
        }
        if (!origen.isEmpty() && !ORIGENES.contains(origen)) {
            throw new BusinessValidationException("El origen del pago no es válido.");
        }
        if (filtro.desde() != null && filtro.hasta() != null && filtro.hasta().isBefore(filtro.desde())) {
            throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        Instant desde = filtro.desde() == null ? LocalDate.of(1, 1, 1).atStartOfDay(LIMA).toInstant()
                : filtro.desde().atStartOfDay(LIMA).toInstant();
        Instant hasta = filtro.hasta() == null ? LocalDate.of(9999, 12, 31).plusDays(1).atStartOfDay(LIMA).toInstant()
                : filtro.hasta().plusDays(1).atStartOfDay(LIMA).toInstant();
        return new Consulta(filtro.texto() == null ? "" : filtro.texto().strip().toLowerCase(), estado,
                filtro.medio() == null ? "" : filtro.medio().strip().toLowerCase(), origen, desde, hasta);
    }

    private static ReportePagoResponse fila(Pago p) {
        var matricula = p.getMatricula();
        var usuario = matricula.getUsuario();
        boolean suma = ("APROBADO".equals(p.getEstado()) || "REGISTRADO_MANUAL".equals(p.getEstado()))
                && p.getImporte().signum() > 0;
        String referencia = p.getOperacionProveedor() != null ? p.getOperacionProveedor() : p.getReferenciaExterna();
        return new ReportePagoResponse(p.getId(), p.getResultadoEn() != null ? p.getResultadoEn() : p.getIniciadoEn(),
                usuario.getPersona().nombreCompleto(), usuario.getCorreo(), matricula.getCurso().getId(),
                matricula.getCurso().getTitulo(), p.getImporte(), p.getMedio(), p.getEstado(), p.getNumeroPedido(),
                referencia, p.getOrigen(), p.getMotivo(), suma);
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private static String mayusculas(String valor) {
        return valor == null ? "" : valor.strip().toUpperCase();
    }
}
