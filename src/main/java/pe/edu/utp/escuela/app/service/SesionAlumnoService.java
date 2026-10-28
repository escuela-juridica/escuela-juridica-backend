package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.IngresarSesionResponse;
import pe.edu.utp.escuela.app.dto.SesionCalendarioResponse;
import pe.edu.utp.escuela.app.entity.Asistencia;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.AsistenciaRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-026 — Participar en sesiones en vivo: calendario del alumno, ingreso con registro automático
 * de asistencia dentro de la ventana de la sesión, y los jobs que cierran asistencias pendientes y
 * envían el recordatorio del día anterior.
 */
@Service
@RequiredArgsConstructor
public class SesionAlumnoService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(LIMA);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(LIMA);

    private final MatriculaRepository matriculas;
    private final LeccionRepository lecciones;
    private final AsistenciaRepository asistencias;
    private final MaterialLeccionRepository materiales;
    private final NotificacionRepository notificaciones;
    private final UsuarioRepository usuarios;
    private final MailService mailService;
    private final ProgresoService progresoService;
    private final CurrentUserService actual;
    private final Clock clock;

    // ---------------------------------------------------------------- Calendario --

    @Transactional(readOnly = true)
    public List<SesionCalendarioResponse> calendario(LocalDate desde, LocalDate hasta) {
        usuarioActualAlumno();
        Long usuarioId = actual.get().userId();

        LocalDate hoy = LocalDate.now(clock.withZone(LIMA));
        LocalDate inicioRango = desde != null ? desde : hoy.withDayOfMonth(1);
        LocalDate finRango = hasta != null ? hasta : inicioRango.withDayOfMonth(inicioRango.lengthOfMonth());
        if (finRango.isBefore(inicioRango)) {
            throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial.");
        }

        List<Long> cursoIds = matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(usuarioId).stream()
                .filter(m -> "ACTIVA".equals(m.getEstado()))
                .map(m -> m.getCurso().getId())
                .distinct()
                .toList();
        if (cursoIds.isEmpty()) {
            return List.of();
        }

        List<Leccion> sesiones = lecciones.buscarSesionesEnVivoDeCursosEntre(
                cursoIds, inicio(inicioRango), finExclusivo(finRango));
        if (sesiones.isEmpty()) {
            return List.of();
        }

        Instant ahora = clock.instant();
        List<Long> idsRealizadas = sesiones.stream()
                .filter(l -> "REALIZADA".equals(estadoSesion(l, ahora)))
                .map(Leccion::getId)
                .toList();
        Set<Long> conGrabacion = idsRealizadas.isEmpty() ? Set.of()
                : Set.copyOf(materiales.buscarLeccionesConMaterialActivo(idsRealizadas));

        return sesiones.stream().map(l -> {
            String estado = estadoSesion(l, ahora);
            String enlace = "EN_VENTANA".equals(estado) ? l.getEnlaceReunion() : null;
            boolean grabacionDisponible = "REALIZADA".equals(estado) && conGrabacion.contains(l.getId());
            return new SesionCalendarioResponse(
                    l.getId(), l.getModulo().getCurso().getId(), l.getModulo().getCurso().getTitulo(),
                    l.getTitulo(), l.getFechaHoraInicio(), l.getFechaHoraFin(), estado, enlace,
                    grabacionDisponible);
        }).toList();
    }

    // ---------------------------------------------------------------- Ingresar --

    @Transactional
    public IngresarSesionResponse ingresar(Long leccionId) {
        Usuario usuario = usuarioActualAlumno();
        Leccion leccion = lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("La sesión no existe."));
        if (!"EN_VIVO".equals(leccion.getTipo())) {
            throw new BusinessValidationException("Esta lección no es una sesión en vivo.");
        }
        if ("CANCELADA".equals(leccion.getEstado())) {
            throw new BusinessValidationException("La sesión fue cancelada.");
        }

        Long cursoId = leccion.getModulo().getCurso().getId();
        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(usuario.getId(), cursoId)
                .orElseThrow(() -> new BusinessValidationException("No tienes una matrícula en este curso."));
        if (!accesoEfectivo(matricula)) {
            throw new BusinessValidationException("Tu matrícula no tiene acceso vigente a este curso.");
        }

        Instant ahora = clock.instant();
        if (leccion.getFechaHoraInicio() == null || leccion.getFechaHoraFin() == null
                || ahora.isBefore(leccion.getFechaHoraInicio()) || ahora.isAfter(leccion.getFechaHoraFin())) {
            throw new BusinessValidationException(
                    "La sesión solo está disponible entre la hora de inicio y la hora de fin.");
        }

        asistencias.findByMatricula_IdAndLeccion_Id(matricula.getId(), leccion.getId())
                .orElseGet(() -> {
                    Asistencia nueva = new Asistencia();
                    nueva.setMatricula(matricula);
                    nueva.setLeccion(leccion);
                    nueva.setEstado("PRESENTE");
                    nueva.setRegistradaEn(ahora);
                    nueva.setModificadaEn(ahora);
                    return asistencias.save(nueva);
                });
        return new IngresarSesionResponse(leccion.getEnlaceReunion());
    }

    // ---------------------------------------------------------------- Porcentaje de asistencia --

    @Transactional(readOnly = true)
    public Integer porcentajeAsistencia(Long matriculaId) {
        Matricula matricula = matriculas.findById(matriculaId)
                .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
        Long cursoId = matricula.getCurso().getId();
        Instant desde = matricula.getFechaMatricula();

        List<Leccion> elegibles = lecciones.buscarSesionesEnVivoDeCurso(cursoId).stream()
                .filter(l -> !"CANCELADA".equals(l.getEstado()))
                .filter(l -> desde == null || (l.getFechaHoraInicio() != null && l.getFechaHoraInicio().isAfter(desde)))
                .toList();
        if (elegibles.isEmpty()) {
            return null;
        }

        Set<Long> idsElegibles = elegibles.stream().map(Leccion::getId).collect(Collectors.toSet());
        long asistidas = asistencias.findByMatricula_Id(matriculaId).stream()
                .filter(a -> idsElegibles.contains(a.getLeccion().getId()))
                .filter(a -> "PRESENTE".equals(a.getEstado()) || "JUSTIFICADA".equals(a.getEstado()))
                .count();
        return (int) Math.round(asistidas * 100.0 / elegibles.size());
    }

    // ---------------------------------------------------------------- Jobs programados --

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    void cerrarAsistenciasPendientesProgramado() {
        Instant ahora = clock.instant();
        List<Leccion> finalizadas = lecciones.buscarSesionesFinalizadasNoCanceladas(ahora);
        for (Leccion leccion : finalizadas) {
            Long cursoId = leccion.getModulo().getCurso().getId();
            for (Matricula matricula : matriculas.findByEstadoAndCurso_Id("ACTIVA", cursoId)) {
                if (!esElegible(leccion, matricula)) {
                    continue;
                }
                Optional<Asistencia> existente =
                        asistencias.findByMatricula_IdAndLeccion_Id(matricula.getId(), leccion.getId());
                String estadoFinal;
                if (existente.isPresent()) {
                    estadoFinal = existente.get().getEstado();
                } else {
                    Asistencia ausente = new Asistencia();
                    ausente.setMatricula(matricula);
                    ausente.setLeccion(leccion);
                    ausente.setEstado("AUSENTE");
                    ausente.setRegistradaEn(ahora);
                    ausente.setModificadaEn(ahora);
                    asistencias.save(ausente);
                    estadoFinal = "AUSENTE";
                }
                progresoService.completarPorAsistencia(matricula, leccion, estadoFinal);
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 40_000)
    @Transactional
    void enviarRecordatoriosSesionProgramado() {
        LocalDate maniana = LocalDate.now(clock.withZone(LIMA)).plusDays(1);
        Instant desde = maniana.atStartOfDay(LIMA).toInstant();
        Instant hasta = maniana.plusDays(1).atStartOfDay(LIMA).toInstant();
        List<Leccion> sesiones = lecciones.buscarSesionesEnVivoQueInicianEntre(desde, hasta);
        for (Leccion leccion : sesiones) {
            Long cursoId = leccion.getModulo().getCurso().getId();
            for (Matricula matricula : matriculas.findByEstadoAndCurso_Id("ACTIVA", cursoId)) {
                if (!esElegible(leccion, matricula)) {
                    continue;
                }
                enviarRecordatorio(leccion, matricula);
            }
        }
    }

    private void enviarRecordatorio(Leccion leccion, Matricula matricula) {
        String tipo = "RECORDATORIO_SESION_" + leccion.getId() + "_" + matricula.getId();
        Optional<Notificacion> existente = notificaciones.findTopByTipoOrderByCreadoEnDesc(tipo);
        if (existente.isPresent() && "ENVIADO".equals(existente.get().getEstadoEnvio())) {
            return;
        }
        Notificacion n = existente.orElseGet(() -> {
            Notificacion nueva = new Notificacion();
            nueva.setUsuario(matricula.getUsuario());
            nueva.setTipo(tipo);
            nueva.setDestinatario(matricula.getUsuario().getCorreo());
            nueva.setAsunto("Recordatorio de sesión en vivo: " + leccion.getTitulo());
            return nueva;
        });
        n.setIntentosEnvio(n.getIntentosEnvio() + 1);
        try {
            String horario = HORA.format(leccion.getFechaHoraInicio())
                    + (leccion.getFechaHoraFin() != null ? " - " + HORA.format(leccion.getFechaHoraFin()) : "");
            mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(),
                    "mail/recordatorio-sesion.html", Map.of(
                            "nombre", matricula.getUsuario().getPersona().getNombres(),
                            "curso", leccion.getModulo().getCurso().getTitulo(),
                            "sesion", leccion.getTitulo(),
                            "fecha", FECHA.format(leccion.getFechaHoraInicio()),
                            "horario", horario)));
            n.setEstadoEnvio("ENVIADO");
            n.setEnviadoEn(clock.instant());
            n.setUltimoError(null);
        } catch (RuntimeException exception) {
            n.setEstadoEnvio("ERROR");
            n.setUltimoError(exception.getMessage());
        }
        notificaciones.save(n);
    }

    // ---------------------------------------------------------------- Auxiliares --

    /** Regla 1: elegible si es EN_VIVO, no CANCELADA, y su inicio es posterior a la matrícula. */
    private boolean esElegible(Leccion leccion, Matricula matricula) {
        return !"CANCELADA".equals(leccion.getEstado())
                && leccion.getFechaHoraInicio() != null
                && matricula.getFechaMatricula() != null
                && leccion.getFechaHoraInicio().isAfter(matricula.getFechaMatricula());
    }

    private String estadoSesion(Leccion l, Instant ahora) {
        if ("CANCELADA".equals(l.getEstado())) {
            return "CANCELADA";
        }
        if (l.getFechaHoraInicio() != null && ahora.isBefore(l.getFechaHoraInicio())) {
            return "FUTURA";
        }
        if (l.getFechaHoraFin() != null && ahora.isAfter(l.getFechaHoraFin())) {
            return "REALIZADA";
        }
        return "EN_VENTANA";
    }

    /** Acceso efectivo de una matrícula: misma fórmula que MatriculaService.respuesta(). */
    private boolean accesoEfectivo(Matricula m) {
        Instant ahora = clock.instant();
        boolean vigente = m.getFechaVencimiento() == null || !ahora.isAfter(m.getFechaVencimiento());
        boolean inicio = m.getCurso().getFechaInicio() == null
                || !LocalDate.now(clock.withZone(LIMA)).isBefore(m.getCurso().getFechaInicio());
        return "ACTIVA".equals(m.getEstado()) && vigente && inicio && m.getUsuario().isActivo()
                && m.getUsuario().getCorreoVerificadoEn() != null && !m.getUsuario().isRequiereCambioContrasena();
    }

    private Usuario usuarioActualAlumno() {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
        return usuarios.findWithPersonaById(actual.get().userId())
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe."));
    }

    private static Instant inicio(LocalDate fecha) {
        return fecha.atStartOfDay(LIMA).toInstant();
    }

    private static Instant finExclusivo(LocalDate fecha) {
        return fecha.plusDays(1).atStartOfDay(LIMA).toInstant();
    }
}
