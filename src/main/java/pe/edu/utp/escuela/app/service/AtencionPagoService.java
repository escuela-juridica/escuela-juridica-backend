package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AtencionPagoResponse;
import pe.edu.utp.escuela.app.dto.AtencionPagoResponse.CursoInfo;
import pe.edu.utp.escuela.app.dto.AtencionPagoResponse.MatriculaInfo;
import pe.edu.utp.escuela.app.dto.AtencionPagoResponse.PagoInfo;
import pe.edu.utp.escuela.app.dto.AtencionPagoResponse.PagoRelacionado;
import pe.edu.utp.escuela.app.entity.AtencionMatriculaPago;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.exception.BusinessException;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.AtencionMatriculaPagoRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.PagoRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-037 — Atención de excepciones de matrículas y pagos (administrador). ESEJUR detecta el caso (aprobación tardía
 * tras una cancelación, resultado contradictorio, sobrecupo); aquí la Escuela solo consulta la evidencia y registra
 * qué atención brindó. Nunca cambia el resultado de Culqi, el estado de la matrícula ni el acceso, ni devuelve dinero.
 */
@Service
@RequiredArgsConstructor
public class AtencionPagoService {
    private static final Set<String> ESTADOS = Set.of(AtencionMatriculaPago.PENDIENTE, AtencionMatriculaPago.ATENDIDO);
    private static final Set<String> TIPOS = Set.of(AtencionMatriculaPago.PAGO_TARDIO,
            AtencionMatriculaPago.CURSO_CANCELADO, AtencionMatriculaPago.MATRICULA_CANCELADA,
            AtencionMatriculaPago.SOBRECUPO);

    private final AtencionMatriculaPagoRepository atenciones;
    private final PagoRepository pagos;
    private final MatriculaRepository matriculas;
    private final UsuarioRepository usuarios;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AtencionPagoResponse> listar(String estado, String tipo, String texto, Pageable pageable) {
        exigirAdministrador();
        String e = mayusculas(estado);
        String t = mayusculas(tipo);
        if (!e.isEmpty() && !ESTADOS.contains(e)) {
            throw new BusinessValidationException("El estado no es válido.");
        }
        if (!t.isEmpty() && !TIPOS.contains(t)) {
            throw new BusinessValidationException("El tipo de excepción no es válido.");
        }
        return atenciones.buscar(texto == null ? "" : texto.strip().toLowerCase(), e, t, pageable)
                .map(a -> responder(a, false));
    }

    @Transactional(readOnly = true)
    public AtencionPagoResponse detalle(Long id) {
        exigirAdministrador();
        return responder(cargar(id), true);
    }

    /** Cantidad de excepciones pendientes, para avisar en la administración. */
    @Transactional(readOnly = true)
    public long pendientes() {
        exigirAdministrador();
        return atenciones.countByEstado(AtencionMatriculaPago.PENDIENTE);
    }

    /**
     * Cierra la excepción con la descripción de lo resuelto fuera de ESEJUR, conservando fecha y responsable. Si el
     * caso agrupa varios pagos (sobrecupo), todos quedan atendidos. Atender de nuevo un caso cerrado se rechaza.
     */
    @Transactional
    public AtencionPagoResponse atender(Long id, String resultado) {
        exigirAdministrador();
        AtencionMatriculaPago a = cargar(id);
        if (AtencionMatriculaPago.ATENDIDO.equals(a.getEstado())) {
            throw new BusinessException(HttpStatus.CONFLICT, "YA_ATENDIDO", "Esta excepción ya fue atendida.");
        }
        var ahora = clock.instant();
        Long adminId = actual.get().userId();
        a.setEstado(AtencionMatriculaPago.ATENDIDO);
        a.setResultado(resultado.strip());
        a.setAtendidoEn(ahora);
        a.setAtendidoPorUsuarioId(adminId);
        atenciones.save(a);

        List<Pago> afectados = AtencionMatriculaPago.SOBRECUPO.equals(a.getTipo())
                ? pagos.findByMatricula_Curso_IdAndRequiereAtencionTrueAndAtendidoEnIsNullOrderByIdAsc(
                        a.getMatricula().getCurso().getId())
                : (a.getPago() == null ? List.<Pago>of() : List.of(a.getPago()));
        for (Pago p : afectados) {
            if (!atenciones.existsByPago_IdAndEstado(p.getId(), AtencionMatriculaPago.PENDIENTE)
                    || AtencionMatriculaPago.SOBRECUPO.equals(a.getTipo())) {
                p.setRequiereAtencion(false);
                p.setAtendidoEn(ahora);
                p.setAtendidoPorUsuarioId(adminId);
                pagos.save(p);
            }
        }
        return responder(a, true);
    }

    private AtencionPagoResponse responder(AtencionMatriculaPago a, boolean conDetalle) {
        Matricula m = a.getMatricula();
        Curso curso = m.getCurso();
        Pago p = a.getPago();
        String atendidoPor = a.getAtendidoPorUsuarioId() == null ? null
                : usuarios.findWithPersonaById(a.getAtendidoPorUsuarioId()).map(u -> u.getPersona().nombreCompleto())
                        .orElse(null);
        PagoInfo pago = p == null ? null : new PagoInfo(p.getId(), p.getNumeroPedido(), p.getEstado(), p.getImporte(),
                p.getMedio(), p.getOperacionProveedor() != null ? p.getOperacionProveedor() : p.getReferenciaExterna(),
                p.getResultadoEn(), p.getConstanciaNumero());
        MatriculaInfo matricula = null;
        CursoInfo cursoInfo = null;
        List<PagoRelacionado> relacionados = List.of();
        if (conDetalle) {
            matricula = new MatriculaInfo(m.getEstado(), m.getFormaIngreso(), m.getFechaMatricula(),
                    "ACTIVA".equals(m.getEstado()) ? "CONCEDIDO" : "SIN_ACCESO");
            long activas = matriculas.contarActivas(List.of(curso.getId())).getOrDefault(curso.getId(), 0L);
            Integer cupo = curso.getCupoMaximo();
            cursoInfo = new CursoInfo(curso.getEstadoCurso() == null ? null : curso.getEstadoCurso().getCodigo(), cupo,
                    activas, curso.getFechaCierreMatricula(), cupo != null && activas > cupo);
            if (AtencionMatriculaPago.SOBRECUPO.equals(a.getTipo())) {
                relacionados = pagos.findByMatricula_Curso_IdAndRequiereAtencionTrueAndAtendidoEnIsNullOrderByIdAsc(
                        curso.getId()).stream()
                        .map(r -> new PagoRelacionado(r.getId(), r.getNumeroPedido(),
                                r.getMatricula().getUsuario().getPersona().nombreCompleto(), r.getImporte(),
                                r.getEstado()))
                        .toList();
            }
        }
        return new AtencionPagoResponse(a.getId(), a.getTipo(), a.getEstado(), a.getDescripcion(), a.getResultado(),
                a.getCreadoEn(), a.getAtendidoEn(), atendidoPor, m.getId(),
                m.getUsuario().getPersona().nombreCompleto(), m.getUsuario().getCorreo(), curso.getId(),
                curso.getTitulo(), pago, matricula, cursoInfo, relacionados);
    }

    private AtencionMatriculaPago cargar(Long id) {
        return atenciones.findWithDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La excepción no existe."));
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
