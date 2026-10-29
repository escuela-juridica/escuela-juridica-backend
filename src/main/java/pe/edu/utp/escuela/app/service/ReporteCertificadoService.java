package pe.edu.utp.escuela.app.service;

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
import pe.edu.utp.escuela.app.dto.OpcionAsistenciaResponse;
import pe.edu.utp.escuela.app.dto.ReporteCertificadoResponse;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.EntidadCertificadoraRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-044 — Reporte de certificados emitidos (administrador). La pantalla y la exportación a Excel salen
 * de la misma consulta y muestran la versión vigente de cada certificado (lo ya congelado al emitir).
 * Nunca se incluyen documento de identidad, nota, firmas ni el PDF. El rango se aplica a la fecha de
 * emisión.
 */
@Service
@RequiredArgsConstructor
public class ReporteCertificadoService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<String> NIVELES = Set.of("NORMAL", "REFRENDADO");
    private static final Set<String> ESTADOS = Set.of("VIGENTE", "ANULADO");
    private static final Set<String> FORMAS = Set.of("AUTOMATICA", "SOLICITADA", "MANUAL");

    private final CertificadoRepository certificados;
    private final EntidadCertificadoraRepository entidades;
    private final CurrentUserService actual;

    /** Filtros del reporte (todos opcionales); una cadena vacía significa sin filtro. */
    public record Filtro(String texto, Long cursoId, String nivel, String entidad, String estado, String forma,
            LocalDate desde, LocalDate hasta) {
    }

    @Transactional(readOnly = true)
    public Page<ReporteCertificadoResponse> paginado(Filtro filtro, Pageable pageable) {
        Consulta q = preparar(filtro);
        return certificados.buscarReporte(q.texto, filtro.cursoId(), q.nivel, q.estado, q.forma, q.entidad, q.desde,
                q.hasta, pageable).map(ReporteCertificadoService::fila);
    }

    @Transactional(readOnly = true)
    public List<ReporteCertificadoResponse> completo(Filtro filtro) {
        Consulta q = preparar(filtro);
        return certificados.exportarReporte(q.texto, filtro.cursoId(), q.nivel, q.estado, q.forma, q.entidad, q.desde,
                q.hasta).stream().map(ReporteCertificadoService::fila).toList();
    }

    /** Entidades certificadoras para el filtro (el nombre tal como se imprime). */
    @Transactional(readOnly = true)
    public List<OpcionAsistenciaResponse> entidades() {
        exigirAdministrador();
        return entidades.findAll().stream().filter(e -> e.isActivo())
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(e -> new OpcionAsistenciaResponse(e.getId(), e.getNombre(), null, null)).toList();
    }

    private record Consulta(String texto, String nivel, String estado, String forma, String entidad,
            Instant desde, Instant hasta) {
    }

    private Consulta preparar(Filtro filtro) {
        exigirAdministrador();
        String nivel = mayusculas(filtro.nivel());
        String estado = mayusculas(filtro.estado());
        String forma = mayusculas(filtro.forma());
        if (!nivel.isEmpty() && !NIVELES.contains(nivel)) {
            throw new BusinessValidationException("Nivel no válido.");
        }
        if (!estado.isEmpty() && !ESTADOS.contains(estado)) {
            throw new BusinessValidationException("Estado no válido.");
        }
        if (!forma.isEmpty() && !FORMAS.contains(forma)) {
            throw new BusinessValidationException("Forma de emisión no válida.");
        }
        if (filtro.desde() != null && filtro.hasta() != null && filtro.hasta().isBefore(filtro.desde())) {
            throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        Instant desde = filtro.desde() == null ? LocalDate.of(1, 1, 1).atStartOfDay(LIMA).toInstant()
                : filtro.desde().atStartOfDay(LIMA).toInstant();
        Instant hasta = filtro.hasta() == null ? LocalDate.of(9999, 12, 31).plusDays(1).atStartOfDay(LIMA).toInstant()
                : filtro.hasta().plusDays(1).atStartOfDay(LIMA).toInstant();
        return new Consulta(filtro.texto() == null ? "" : filtro.texto().strip().toLowerCase(), nivel, estado, forma,
                filtro.entidad() == null ? "" : filtro.entidad().strip().toLowerCase(), desde, hasta);
    }

    private static ReporteCertificadoResponse fila(Certificado c) {
        return new ReporteCertificadoResponse(c.getId(), c.getCodigo(), c.getNombresImpresos(),
                c.getLogro().getMatricula().getCurso().getId(), c.getTituloCursoImpreso(), c.getNivel(),
                c.getEntidadImpresa(), c.getLogro().getFechaFinalizacion(), c.getEmitidoEn(),
                Certificado.ANULADO.equals(c.getEstado()) ? Certificado.ANULADO : Certificado.VIGENTE, c.getTipoEmision());
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
