package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AsistenciaSesionResponse;
import pe.edu.utp.escuela.app.dto.CancelarSesionRequest;
import pe.edu.utp.escuela.app.dto.CorregirAsistenciaRequest;
import pe.edu.utp.escuela.app.dto.CorregirEnlaceSesionRequest;
import pe.edu.utp.escuela.app.dto.HistorialSesionResponse;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionRequest;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionResultado;
import pe.edu.utp.escuela.app.entity.Asistencia;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.ExcepcionAcademica;
import pe.edu.utp.escuela.app.entity.HistorialSesion;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.event.ReevaluarCertificacionEvent;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.AsistenciaRepository;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.HistorialSesionRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-027 — Administrar cambios en sesiones en vivo: reprogramar o cancelar una sesión futura
 * (con motivo e historial conservado), y corregir el enlace o una asistencia de una sesión ya
 * realizada. No reemplaza {@code ContenidoService#actualizarSesion} (HU-012): esa es la
 * programación inicial; esta historia cubre los cambios posteriores.
 */
@Service
@RequiredArgsConstructor
public class AdministrarSesionesService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final LeccionRepository lecciones;
    private final CursoRepository cursos;
    private final MatriculaRepository matriculas;
    private final AsistenciaRepository asistencias;
    private final HistorialSesionRepository historial;
    private final NotificacionRepository notificaciones;
    private final ExcepcionAcademicaRepository excepciones;
    private final MailService mailService;
    private final ProgresoService progresoService;
    private final ApplicationEventPublisher eventos;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional
    public ReprogramarSesionResultado reprogramar(Long leccionId, ReprogramarSesionRequest p) {
        exigirAdministrador();
        Leccion l = leccionSesionEditable(leccionId);

        if (p.motivo() == null || p.motivo().isBlank()) {
            throw new BusinessValidationException("Indica el motivo de la reprogramación.");
        }
        if (!p.nuevaFechaHoraFin().isAfter(p.nuevaFechaHoraInicio())) {
            throw new BusinessValidationException("La hora de fin debe ser posterior al inicio.");
        }

        Curso curso = l.getModulo().getCurso();
        LocalDate finNuevo = p.nuevaFechaHoraFin().atZone(LIMA).toLocalDate();
        if (curso.getFechaFin() != null && finNuevo.isAfter(curso.getFechaFin())) {
            // No se guarda nada todavía: el frontend debe ampliar primero la fecha de fin del
            // curso y volver a llamar a este mismo endpoint.
            return new ReprogramarSesionResultado(false, true, finNuevo);
        }

        Instant inicioAnterior = l.getFechaHoraInicio();
        Instant finAnterior = l.getFechaHoraFin();
        String enlaceAnterior = l.getEnlaceReunion();

        l.setFechaHoraInicio(p.nuevaFechaHoraInicio());
        l.setFechaHoraFin(p.nuevaFechaHoraFin());
        lecciones.save(l);

        guardarHistorial(l, "REPROGRAMACION", inicioAnterior, finAnterior, enlaceAnterior,
                p.nuevaFechaHoraInicio(), p.nuevaFechaHoraFin(), enlaceAnterior, p.motivo());
        // Se avisa aunque el recordatorio del horario anterior ya hubiera salido: no hay
        // deduplicación aquí (a diferencia del recordatorio de HU-026).
        avisarCambio(l, "Tu sesión fue reprogramada", p.motivo());

        return new ReprogramarSesionResultado(true, false, null);
    }

    /** HU-027 — Ampliar primero la fecha de fin del curso cuando una reprogramación la supera. */
    @Transactional
    public void ampliarFinDeCurso(Long cursoId, LocalDate nuevaFechaFin) {
        exigirAdministrador();
        Curso curso = cursos.findById(cursoId)
                .orElseThrow(() -> new BusinessValidationException("El curso no existe."));
        if (curso.getFechaFin() != null && nuevaFechaFin.isBefore(curso.getFechaFin())) {
            throw new BusinessValidationException("La nueva fecha de fin no puede ser anterior a la actual.");
        }
        curso.setFechaFin(nuevaFechaFin);
        cursos.save(curso);
        recalcularFechasCertificadosPendientes(cursoId, nuevaFechaFin);
    }

    @Transactional
    public void cancelar(Long leccionId, CancelarSesionRequest p) {
        exigirAdministrador();
        Leccion l = leccionSesionEditable(leccionId);
        if (p.motivo() == null || p.motivo().isBlank()) {
            throw new BusinessValidationException("Indica el motivo de la cancelación.");
        }

        guardarHistorial(l, "CANCELACION", l.getFechaHoraInicio(), l.getFechaHoraFin(),
                l.getEnlaceReunion(), null, null, null, p.motivo());

        l.setEstado("CANCELADA");
        l.setMotivoCancelacion(p.motivo());
        lecciones.save(l);
        // No se toca curso.horasAcademicas ni curso.fechaFin automáticamente: si la cancelación
        // reduce lo ofrecido, administración debe revisar esos valores a mano (nunca se
        // recalculan solos). El recordatorio de HU-026 nunca se dispara para esta sesión porque
        // su consulta ya filtra estado <> 'CANCELADA'.

        avisarCambio(l, "Tu sesión fue cancelada", p.motivo());
    }

    @Transactional
    public void corregirEnlace(Long leccionId, CorregirEnlaceSesionRequest p) {
        exigirAdministrador();
        Leccion l = lecciones.findById(leccionId)
                .orElseThrow(() -> new BusinessValidationException("La sesión no existe."));
        if (l.getFechaHoraFin() == null || l.getFechaHoraFin().isAfter(clock.instant())) {
            throw new BusinessValidationException("Solo se corrige el enlace de una sesión ya realizada.");
        }
        l.setEnlaceReunion(p.enlaceReunion());
        lecciones.save(l);
    }

    @Transactional
    public void corregirAsistencia(Long asistenciaId, CorregirAsistenciaRequest p) {
        exigirAdministrador();
        Asistencia a = asistencias.findById(asistenciaId)
                .orElseThrow(() -> new BusinessValidationException("La asistencia no existe."));
        if (p.motivoCorreccion() == null || p.motivoCorreccion().isBlank()) {
            throw new BusinessValidationException("Indica el motivo de la corrección.");
        }
        if (!List.of("PRESENTE", "AUSENTE", "JUSTIFICADA").contains(p.nuevoEstado())) {
            throw new BusinessValidationException("El estado de asistencia no es válido.");
        }
        String estadoAnterior = a.getEstado();
        // Nunca se borra el registro original: el ajuste queda en el mismo campo.
        a.setEstado(p.nuevoEstado());
        a.setMotivoCorreccion(p.motivoCorreccion());
        a.setRegistradaPorUsuarioId(actual.get().userId());
        a.setModificadaEn(clock.instant());
        asistencias.save(a);

        // HU-031: toda corrección conserva quién, cuándo, por qué y el valor anterior.
        ExcepcionAcademica excepcion = new ExcepcionAcademica();
        excepcion.setMatricula(a.getMatricula());
        excepcion.setTipo(ExcepcionAcademica.CORREGIR_ASISTENCIA);
        excepcion.setReferenciaTipo("ASISTENCIA");
        excepcion.setReferenciaId(a.getId());
        excepcion.setValorAnterior(estadoAnterior);
        excepcion.setValorNuevo(p.nuevoEstado());
        excepcion.setMotivo(p.motivoCorreccion());
        excepcion.setRealizadoPorUsuarioId(actual.get().userId());
        excepcion.setRealizadoEn(clock.instant());
        excepciones.save(excepcion);
        progresoService.completarPorAsistencia(a.getMatricula(), a.getLeccion(), p.nuevoEstado());
        // HU-032: la asistencia corregida puede cambiar el cumplimiento del curso.
        eventos.publishEvent(new ReevaluarCertificacionEvent(a.getMatricula().getId()));
    }

    /** HU-027 — Lista la asistencia de una sesión ya realizada, para el modal "Corregir". */
    @Transactional(readOnly = true)
    public List<AsistenciaSesionResponse> listarAsistencias(Long leccionId) {
        exigirAdministrador();
        return asistencias.buscarDeLeccionConAlumno(leccionId).stream()
                .map(a -> new AsistenciaSesionResponse(a.getId(), a.getMatricula().getId(),
                        a.getMatricula().getUsuario().getPersona().nombreCompleto(), a.getEstado()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistorialSesionResponse> historial(Long leccionId) {
        return historial.findByLeccion_IdOrderByRealizadoEnDesc(leccionId).stream()
                .map(h -> new HistorialSesionResponse(h.getAccion(), h.getInicioAnterior(),
                        h.getFinAnterior(), h.getInicioNuevo(), h.getFinNuevo(), h.getMotivo(),
                        h.getRealizadoPorUsuarioId(), h.getRealizadoEn()))
                .toList();
    }

    private Leccion leccionSesionEditable(Long leccionId) {
        Leccion l = lecciones.findById(leccionId)
                .orElseThrow(() -> new BusinessValidationException("La sesión no existe."));
        if (!"EN_VIVO".equals(l.getTipo())) {
            throw new BusinessValidationException("Esta lección no es una sesión en vivo.");
        }
        if (l.getFechaHoraFin() != null && !l.getFechaHoraFin().isAfter(clock.instant())) {
            throw new BusinessValidationException("No puedes modificar una sesión ya realizada.");
        }
        if ("CANCELADA".equals(l.getEstado())) {
            throw new BusinessValidationException("Esta sesión ya está cancelada.");
        }
        return l;
    }

    private void guardarHistorial(Leccion l, String accion, Instant inicioAnt, Instant finAnt,
            String enlaceAnt, Instant inicioNuevo, Instant finNuevo, String enlaceNuevo, String motivo) {
        HistorialSesion h = new HistorialSesion();
        h.setLeccion(l);
        h.setAccion(accion);
        h.setInicioAnterior(inicioAnt);
        h.setFinAnterior(finAnt);
        h.setEnlaceAnterior(enlaceAnt);
        h.setInicioNuevo(inicioNuevo);
        h.setFinNuevo(finNuevo);
        h.setEnlaceNuevo(enlaceNuevo);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(actual.get().userId());
        h.setRealizadoEn(clock.instant());
        historial.save(h);
    }

    private void avisarCambio(Leccion l, String asunto, String motivo) {
        Long cursoId = l.getModulo().getCurso().getId();
        List<Matricula> alumnos = matriculas.findByEstadoAndCurso_Id("ACTIVA", cursoId);
        for (Matricula m : alumnos) {
            Notificacion n = new Notificacion();
            n.setUsuario(m.getUsuario());
            // A diferencia del recordatorio de HU-026 (que dedupe por tipo fijo), cada
            // reprogramación/cancelación es un evento nuevo que debe avisar siempre: el tipo
            // incluye el instante del cambio para no agruparse con avisos anteriores de la misma
            // lección. clock.millis() (no clock.instant(), más largo) para no exceder el
            // varchar(40) de notificacion.tipo.
            n.setTipo("CAMBIO_SESION_" + l.getId() + "_" + clock.millis());
            n.setDestinatario(m.getUsuario().getCorreo());
            n.setAsunto(asunto + ": " + l.getTitulo());
            try {
                mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(),
                        "mail/cambio-sesion.html", Map.of(
                                "sesion", l.getTitulo(), "motivo", motivo,
                                "inicio", String.valueOf(l.getFechaHoraInicio()))));
                n.setEstadoEnvio("ENVIADO");
                n.setEnviadoEn(clock.instant());
            } catch (MailDeliveryException exception) {
                n.setEstadoEnvio("ERROR");
                n.setUltimoError(exception.getMessage());
            }
            notificaciones.save(n);
        }
    }

    // TODO: EP05 (Evaluación y Certificación) todavía no define cómo se modela la emisión de
    // certificados. Por ahora este método solo documenta el punto de extensión: cuando esa épica
    // exista, aquí se recalcula la fecha programada de los certificados de matrículas con
    // logroCertificacion != null y aún no emitido, para las matrículas de este curso.
    private void recalcularFechasCertificadosPendientes(Long cursoId, LocalDate nuevaFechaFin) {
        // Intencionalmente vacío hasta que EP05 defina la entidad de certificado.
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
