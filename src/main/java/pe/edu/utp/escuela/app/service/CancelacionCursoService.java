package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.utp.escuela.app.dto.CancelacionAfectadoResponse;
import pe.edu.utp.escuela.app.dto.CancelacionCursoResponse;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.HistorialEstadoCurso;
import pe.edu.utp.escuela.app.entity.LogroCertificacion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.EstadoCursoRepository;
import pe.edu.utp.escuela.app.repository.HistorialEstadoCursoRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.LogroCertificacionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.repository.PagoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-038 — Cancelación completa de un curso (administrador). En una sola transacción: el curso pasa a CANCELADO
 * (lo que detiene de inmediato nuevas matrículas y pagos), se cancelan las sesiones futuras y queda registrado un
 * aviso por cada alumno con acceso. Nada se borra: matrículas, pagos, progreso, intentos, asistencia y certificados
 * se conservan, y quienes ya tenían acceso mantienen la consulta de lo que estuvo disponible. Los correos se envían
 * después de confirmar la transacción: un fallo no revierte la cancelación, queda por alumno y se puede reintentar.
 * ESEJUR no devuelve dinero; un APROBADO posterior de Culqi se registra sin dar acceso y se atiende en HU-037.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelacionCursoService {

    private static final String PREFIJO_AVISO = "CURSO_CANCELADO_";

    private final CursoRepository cursos;
    private final EstadoCursoRepository estadosCurso;
    private final HistorialEstadoCursoRepository historial;
    private final MatriculaRepository matriculas;
    private final PagoRepository pagos;
    private final LeccionRepository lecciones;
    private final LogroCertificacionRepository logros;
    private final NotificacionRepository notificaciones;
    private final AdministrarSesionesService sesiones;
    private final MailService mailService;
    private final CurrentUserService actual;
    private final PlatformTransactionManager transacciones;
    private final Clock clock;

    @Value("${esejur.reclamaciones.correo-escuela:informes@esejur.pe}")
    private String canalAtencion;

    // ---------------------------------------------------------------------------------------- consulta --

    /** Resume lo que se afectaría (o, si el curso ya está cancelado, lo que se afectó) y el estado de los avisos. */
    @Transactional(readOnly = true)
    public CancelacionCursoResponse impacto(Long cursoId) {
        exigirAdministrador();
        Curso curso = cursos.findWithDetalleById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso no existe."));
        String codigo = curso.getEstadoCurso().getCodigo();
        boolean operativo = "PUBLICADO".equals(codigo) || "EN_CURSO".equals(codigo);
        long conAcceso = matriculas.countByCurso_IdAndEstado(cursoId, "ACTIVA");
        long finalizados = logros.contarFinalizadosActivos(cursoId);
        String tipo = PREFIJO_AVISO + cursoId;
        long avisos = notificaciones.countByTipo(tipo);
        long enviados = notificaciones.countByTipoAndEstadoEnvio(tipo, "ENVIADO");
        return new CancelacionCursoResponse(curso.getId(), curso.getTitulo(), codigo, operativo,
                operativo ? null : mensajeBloqueo(codigo), conAcceso, finalizados, Math.max(conAcceso - finalizados, 0),
                matriculas.countByCurso_IdAndEstado(cursoId, "PENDIENTE_PAGO"),
                pagos.countByMatricula_Curso_IdAndEstado(cursoId, Pago.PENDIENTE),
                lecciones.contarSesionesFuturasActivas(cursoId, clock.instant()), curso.getCanceladoEn(),
                curso.getMotivoCancelacion(), avisos, enviados, avisos - enviados);
    }

    /** Alumnos con acceso previo y el estado de su aviso, paginados; los avisos fallidos aparecen primero. */
    @Transactional(readOnly = true)
    public Page<CancelacionAfectadoResponse> afectados(Long cursoId, Pageable pageable) {
        exigirAdministrador();
        Page<Notificacion> pagina = notificaciones.findByTipoOrderByEstadoEnvioDescIdAsc(PREFIJO_AVISO + cursoId, pageable);
        List<Long> usuarioIds = pagina.getContent().stream().map(n -> n.getUsuario().getId()).toList();
        Map<Long, Matricula> porUsuario = usuarioIds.isEmpty() ? Map.of()
                : matriculas.findByCurso_IdAndUsuario_IdIn(cursoId, usuarioIds).stream()
                        .collect(Collectors.toMap(m -> m.getUsuario().getId(), Function.identity(), (a, b) -> a));
        Map<Long, LogroCertificacion> logroPorMatricula = porUsuario.isEmpty() ? Map.of()
                : logros.findByMatricula_IdIn(porUsuario.values().stream().map(Matricula::getId).toList()).stream()
                        .collect(Collectors.toMap(l -> l.getMatricula().getId(), Function.identity()));
        return pagina.map(n -> {
            Matricula m = porUsuario.get(n.getUsuario().getId());
            LogroCertificacion logro = m == null ? null : logroPorMatricula.get(m.getId());
            return new CancelacionAfectadoResponse(n.getUsuario().getId(), n.getUsuario().getPersona().nombreCompleto(),
                    n.getDestinatario(), logro != null && logro.getFechaFinalizacion() != null, n.getEstadoEnvio(),
                    n.getIntentosEnvio(), n.getUltimoError(), n.getEnviadoEn());
        });
    }

    // ----------------------------------------------------------------------------------------- cancelar --

    /**
     * Cancela el curso con motivo. Solo PUBLICADO o EN_CURSO; el curso se bloquea mientras se decide para que un pago
     * o una matrícula simultáneos no se cuelen. Los avisos se registran dentro de la transacción y se envían después.
     */
    public CancelacionCursoResponse cancelar(Long cursoId, String motivo) {
        exigirAdministrador();
        Long adminId = actual.get().userId();
        String motivoLimpio = motivo.strip();
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            Curso curso = cursos.bloquearParaMatricula(cursoId)
                    .orElseThrow(() -> new ResourceNotFoundException("El curso no existe."));
            String codigo = curso.getEstadoCurso().getCodigo();
            if (!"PUBLICADO".equals(codigo) && !"EN_CURSO".equals(codigo)) {
                throw new BusinessValidationException(mensajeBloqueo(codigo));
            }
            Instant ahora = clock.instant();
            EstadoCurso anterior = curso.getEstadoCurso();
            EstadoCurso destino = estadosCurso.findByCodigo("CANCELADO")
                    .orElseThrow(() -> new IllegalStateException("Falta el estado CANCELADO"));
            curso.setEstadoCurso(destino);
            curso.setCanceladoEn(ahora);
            curso.setMotivoCancelacion(motivoLimpio);
            cursos.save(curso);

            HistorialEstadoCurso h = new HistorialEstadoCurso();
            h.setCurso(curso);
            h.setEstadoAnterior(anterior);
            h.setEstadoNuevo(destino);
            h.setMotivo("Curso cancelado: " + motivoLimpio);
            h.setRealizadoPorUsuarioId(adminId);
            h.setRealizadoEn(ahora);
            historial.saveAndFlush(h);

            sesiones.cancelarSesionesFuturasDeCurso(cursoId, "Curso cancelado: " + motivoLimpio);

            // Un solo aviso por alumno con acceso (no uno por sesión): queda registrado antes de intentar el correo.
            for (Matricula m : matriculas.findByEstadoAndCurso_Id("ACTIVA", cursoId)) {
                Notificacion n = new Notificacion();
                n.setUsuario(m.getUsuario());
                n.setTipo(PREFIJO_AVISO + cursoId);
                n.setDestinatario(m.getUsuario().getCorreo());
                n.setAsunto("Se canceló el curso: " + curso.getTitulo());
                notificaciones.save(n);
            }
        });
        enviarPendientes(cursoId);
        return impacto(cursoId);
    }

    /** Reintenta los avisos que fallaron o no llegaron a enviarse, sin duplicar los ya enviados. */
    public CancelacionCursoResponse reenviarAvisos(Long cursoId) {
        exigirAdministrador();
        Curso curso = cursos.findWithDetalleById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso no existe."));
        if (!"CANCELADO".equals(curso.getEstadoCurso().getCodigo())) {
            throw new BusinessValidationException("Este curso no está cancelado: no hay avisos que reenviar.");
        }
        enviarPendientes(cursoId);
        return impacto(cursoId);
    }

    private void enviarPendientes(Long cursoId) {
        Curso curso = cursos.findById(cursoId).orElseThrow();
        for (Notificacion n : notificaciones.findByTipoAndEstadoEnvioNotOrderByIdAsc(PREFIJO_AVISO + cursoId, "ENVIADO")) {
            n.setIntentosEnvio(n.getIntentosEnvio() + 1);
            try {
                mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(), "mail/curso-cancelado.html",
                        Map.of("nombre", n.getUsuario().getPersona().getNombres(), "curso", curso.getTitulo(),
                                "motivo", curso.getMotivoCancelacion() == null ? "" : curso.getMotivoCancelacion(),
                                "canal", canalAtencion)));
                n.setEstadoEnvio("ENVIADO");
                n.setEnviadoEn(clock.instant());
                n.setUltimoError(null);
            } catch (MailDeliveryException e) {
                n.setEstadoEnvio("ERROR");
                n.setUltimoError(e.getMessage());
                log.warn("No se pudo avisar la cancelación del curso {} a {}", cursoId, n.getDestinatario(), e);
            }
            notificaciones.save(n);
        }
    }

    private static String mensajeBloqueo(String codigo) {
        return switch (codigo) {
            case "CANCELADO" -> "Este curso ya está cancelado.";
            case "CERRADO" -> "Un curso cerrado terminó de forma normal: ya no puede cancelarse.";
            default -> "Solo un curso publicado o en curso puede cancelarse.";
        };
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
