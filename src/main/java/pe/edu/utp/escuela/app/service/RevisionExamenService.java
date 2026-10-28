package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.BandejaResumenResponse;
import pe.edu.utp.escuela.app.dto.BandejaRevisionRow;
import pe.edu.utp.escuela.app.dto.CalificarIntentoRequest;
import pe.edu.utp.escuela.app.dto.CalificarIntentoRequest.PuntajeRespuestaRequest;
import pe.edu.utp.escuela.app.dto.IntentoPendienteResponse;
import pe.edu.utp.escuela.app.dto.RevisionIntentoResponse;
import pe.edu.utp.escuela.app.dto.RevisionIntentoResponse.PreguntaRevisionResponse;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.IntentoExamen;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.RespuestaIntento;
import pe.edu.utp.escuela.app.entity.RevisionRespuesta;
import pe.edu.utp.escuela.app.event.ReevaluarCertificacionEvent;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.IntentoExamenRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;
import pe.edu.utp.escuela.app.repository.RespuestaIntentoRepository;
import pe.edu.utp.escuela.app.repository.RevisionRespuestaRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-030 — Calificar respuestas abiertas (administrador). La bandeja lista los intentos en
 * PENDIENTE_REVISION ordenados por su fecha límite más cercana y separa los vencidos y los próximos
 * a vencer; vencer solo señala demora, nunca califica ni altera la respuesta.
 *
 * <p>Publicar la calificación es una sola transacción: exige un puntaje por cada respuesta abierta
 * pendiente (entre 0 y el máximo de su pregunta, ambos válidos), guarda el historial en
 * {@code revision_respuesta}, recalcula la nota del intento con la misma fórmula que la corrección
 * automática y lo pasa a CALIFICADO. Un intento ya calificado no se publica dos veces. El aviso
 * por correo va después y su fallo no revierte la nota; se puede reenviar.
 */
@Service
@RequiredArgsConstructor
public class RevisionExamenService {

    /** Un intento "próximo" vence en menos de este tiempo. */
    private static final Duration PROXIMO = Duration.ofHours(24);

    private final RespuestaIntentoRepository respuestas;
    private final RevisionRespuestaRepository revisiones;
    private final IntentoExamenRepository intentos;
    private final ReglaCursoRepository reglasCurso;
    private final NotificacionRepository notificaciones;
    private final MailService mailService;
    private final ApplicationEventPublisher eventos;
    private final CurrentUserService actual;
    private final Clock clock;

    /** Filtros de la bandeja; las cadenas vacías significan "sin filtro". */
    public record Filtro(String texto, Long cursoId, String plazo) {
    }

    @Transactional(readOnly = true)
    public Page<IntentoPendienteResponse> bandeja(Filtro filtro, Pageable pageable) {
        exigirAdministrador();
        String plazo = filtro.plazo() == null ? "" : filtro.plazo().strip().toUpperCase();
        if (!plazo.isEmpty() && !Set.of("VENCIDO", "PROXIMO", "EN_PLAZO").contains(plazo)) {
            throw new BusinessValidationException("Plazo no válido.");
        }
        Instant ahora = clock.instant();
        String texto = filtro.texto() == null ? "" : filtro.texto().strip().toLowerCase();
        // El universo pendiente es pequeño por naturaleza; el plazo no es una columna, así que se
        // clasifica aquí y el servidor pagina el resultado ya filtrado y ordenado por urgencia.
        List<IntentoPendienteResponse> todos = respuestas.bandejaRevision(texto, filtro.cursoId()).stream()
                .map(f -> fila(f, ahora))
                .filter(f -> plazo.isEmpty() || plazo.equals(f.plazo()))
                .toList();
        int inicio = (int) Math.min(pageable.getOffset(), todos.size());
        int fin = Math.min(inicio + pageable.getPageSize(), todos.size());
        return new PageImpl<>(todos.subList(inicio, fin), pageable, todos.size());
    }

    @Transactional(readOnly = true)
    public BandejaResumenResponse resumen() {
        exigirAdministrador();
        Instant ahora = clock.instant();
        List<IntentoPendienteResponse> todos = respuestas.bandejaRevision("", null).stream()
                .map(f -> fila(f, ahora)).toList();
        return new BandejaResumenResponse(todos.size(),
                todos.stream().filter(f -> "PROXIMO".equals(f.plazo())).count(),
                todos.stream().filter(f -> "VENCIDO".equals(f.plazo())).count());
    }

    @Transactional(readOnly = true)
    public RevisionIntentoResponse detalle(Long intentoId) {
        exigirAdministrador();
        IntentoExamen intento = intentos.findById(intentoId)
                .orElseThrow(() -> new ResourceNotFoundException("El intento no existe."));
        return construir(intento);
    }

    /** Publica la calificación completa del intento. Idempotente por estado: un segundo intento falla. */
    @Transactional
    public RevisionIntentoResponse calificar(Long intentoId, CalificarIntentoRequest peticion) {
        exigirAdministrador();
        IntentoExamen intento = intentos.buscarConBloqueo(intentoId)
                .orElseThrow(() -> new ResourceNotFoundException("El intento no existe."));
        if (!intento.estaPendienteRevision()) {
            throw new BusinessValidationException(
                    "Este intento ya fue calificado: la calificación no se puede publicar de nuevo.");
        }

        List<RespuestaIntento> delIntento = respuestas.findByIntentoExamen_Id(intento.getId());
        Map<Long, RespuestaIntento> pendientes = delIntento.stream()
                .filter(r -> RespuestaIntento.PENDIENTE.equals(r.getEstadoRevision()))
                .collect(Collectors.toMap(RespuestaIntento::getId, r -> r));

        Map<Long, PuntajeRespuestaRequest> recibidos = new java.util.HashMap<>();
        for (PuntajeRespuestaRequest p : peticion.respuestas()) {
            if (recibidos.put(p.respuestaId(), p) != null) {
                throw new BusinessValidationException("Una respuesta se calificó dos veces.");
            }
            if (!pendientes.containsKey(p.respuestaId())) {
                throw new BusinessValidationException("Una respuesta no pertenece a este intento o ya fue calificada.");
            }
        }
        if (recibidos.size() < pendientes.size()) {
            long faltan = pendientes.size() - recibidos.size();
            throw new BusinessValidationException("Falta calificar " + faltan
                    + (faltan == 1 ? " respuesta abierta." : " respuestas abiertas."));
        }

        Instant ahora = clock.instant();
        Long revisor = actual.get().userId();
        for (Map.Entry<Long, PuntajeRespuestaRequest> e : recibidos.entrySet()) {
            RespuestaIntento respuesta = pendientes.get(e.getKey());
            BigDecimal puntaje = e.getValue().puntaje();
            BigDecimal maximo = respuesta.getPregunta().getPuntaje();
            if (puntaje.scale() > 2) {
                throw new BusinessValidationException("El puntaje admite hasta dos decimales.");
            }
            if (puntaje.signum() < 0 || puntaje.compareTo(maximo) > 0) {
                throw new BusinessValidationException(
                        "El puntaje debe estar entre 0 y " + maximo.stripTrailingZeros().toPlainString() + ".");
            }
            String observacion = e.getValue().observacion() == null || e.getValue().observacion().isBlank()
                    ? null : e.getValue().observacion().strip();

            RevisionRespuesta revision = new RevisionRespuesta();
            revision.setRespuestaIntento(respuesta);
            revision.setPuntajeAnterior(respuesta.getPuntajeDefinitivo());
            revision.setPuntajeAsignado(puntaje);
            revision.setObservacion(observacion);
            revision.setRevisadoPorUsuarioId(revisor);
            revision.setRevisadoEn(ahora);
            revisiones.save(revision);

            respuesta.setPuntajeDefinitivo(puntaje);
            respuesta.setEstadoRevision(RespuestaIntento.REVISADA);
            respuestas.save(respuesta);
        }

        BigDecimal obtenido = delIntento.stream().map(RespuestaIntento::getPuntajeDefinitivo)
                .filter(p -> p != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nota = ExamenAlumnoService.calcularNota(obtenido, intento.getPuntajeTotal());
        intento.setPuntajeObtenido(obtenido);
        intento.setNota(nota);
        intento.setAprobado(nota.compareTo(reglaDe(intento.getMatricula()).getNotaMinima()) >= 0);
        intento.setEstado(IntentoExamen.CALIFICADO);
        intento.setCalificadoEn(ahora);
        intentos.save(intento);

        avisar(intento);
        // HU-032: la nota definitiva reevalúa finalización y certificación.
        eventos.publishEvent(new ReevaluarCertificacionEvent(intento.getMatricula().getId()));
        return construir(intento);
    }

    /** Reenvía el aviso "Tu examen fue calificado" de un intento ya calificado. */
    @Transactional
    public void reenviarAviso(Long intentoId) {
        exigirAdministrador();
        IntentoExamen intento = intentos.findById(intentoId)
                .orElseThrow(() -> new ResourceNotFoundException("El intento no existe."));
        if (!IntentoExamen.CALIFICADO.equals(intento.getEstado())) {
            throw new BusinessValidationException("Solo se avisa de un intento ya calificado.");
        }
        avisar(intento);
    }

    // ------------------------------------------------------------------ internos --

    private IntentoPendienteResponse fila(BandejaRevisionRow f, Instant ahora) {
        long minutos = Duration.between(ahora, f.fechaLimite()).toMinutes();
        return new IntentoPendienteResponse(f.intentoId(), f.matriculaId(), f.alumno(), f.correo(), f.cursoId(),
                f.curso(), f.examenId(), f.examen(), f.numeroIntento(), f.enviadoEn(), f.fechaLimite(),
                plazo(f.fechaLimite(), ahora), minutos, f.respuestasPendientes());
    }

    private static String plazo(Instant limite, Instant ahora) {
        if (limite.isBefore(ahora)) {
            return "VENCIDO";
        }
        return limite.isBefore(ahora.plus(PROXIMO)) ? "PROXIMO" : "EN_PLAZO";
    }

    private RevisionIntentoResponse construir(IntentoExamen intento) {
        Examen examen = intento.getExamen();
        Matricula matricula = intento.getMatricula();
        List<RespuestaIntento> todas = respuestas.findByIntentoExamen_Id(intento.getId());
        List<RespuestaIntento> abiertas = todas.stream()
                .filter(r -> "RESPUESTA_ABIERTA".equals(r.getPregunta().getTipo()))
                .filter(r -> !RespuestaIntento.NO_APLICA.equals(r.getEstadoRevision()))
                .sorted(Comparator.comparing(r -> r.getPregunta().getOrden()))
                .toList();

        Map<Long, RevisionRespuesta> ultimaRevision = revisiones
                .findByRespuestaIntento_IdInOrderByRevisadoEnDesc(abiertas.stream().map(RespuestaIntento::getId).toList())
                .stream().collect(Collectors.toMap(r -> r.getRespuestaIntento().getId(), r -> r, (a, b) -> a));

        List<PreguntaRevisionResponse> preguntas = abiertas.stream().map(r -> {
            RevisionRespuesta rev = ultimaRevision.get(r.getId());
            return new PreguntaRevisionResponse(r.getId(), r.getPregunta().getOrden(), r.getPregunta().getEnunciado(),
                    r.getPregunta().getPuntaje(), r.getTextoRespuesta(), r.getEstadoRevision(),
                    rev == null ? null : rev.getPuntajeAsignado(), rev == null ? null : rev.getObservacion(),
                    rev == null ? null : rev.getRevisadoEn());
        }).toList();

        BigDecimal objetivo = todas.stream()
                .filter(r -> !"RESPUESTA_ABIERTA".equals(r.getPregunta().getTipo()))
                .map(RespuestaIntento::getPuntajeDefinitivo).filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant limite = todas.stream().map(RespuestaIntento::getFechaLimiteRevision).filter(f -> f != null)
                .min(Comparator.naturalOrder()).orElse(null);
        Instant ahora = clock.instant();
        String plazo = limite == null ? null
                : intento.estaPendienteRevision() ? plazo(limite, ahora) : null;

        return new RevisionIntentoResponse(intento.getId(), intento.getEstado(), matricula.getId(),
                matricula.getUsuario().getPersona().nombreCompleto(), matricula.getUsuario().getCorreo(),
                matricula.getCurso().getId(), matricula.getCurso().getTitulo(), examen.getId(), examen.getTitulo(),
                examen.getTipo(), intento.getNumeroIntento(), intento.getEnviadoEn(), limite, plazo,
                intento.getPuntajeTotal(), objetivo, reglaDe(matricula).getNotaMinima(), intento.getNota(),
                intento.getAprobado(), intento.getCalificadoEn(), preguntas);
    }

    /** El correo se pide después de publicar y un fallo queda registrado sin tocar la nota. */
    private void avisar(IntentoExamen intento) {
        Matricula matricula = intento.getMatricula();
        Notificacion n = new Notificacion();
        n.setUsuario(matricula.getUsuario());
        // El tipo cabe en varchar(40) y es único por envío para que un reenvío no se agrupe con el anterior.
        n.setTipo("EXAMEN_CALIFICADO_" + intento.getId() + "_" + clock.millis());
        n.setDestinatario(matricula.getUsuario().getCorreo());
        n.setAsunto("Tu examen fue calificado: " + intento.getExamen().getTitulo());
        try {
            mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(), "mail/examen-calificado.html",
                    Map.of("alumno", matricula.getUsuario().getPersona().getNombres(),
                            "examen", intento.getExamen().getTitulo(),
                            "curso", matricula.getCurso().getTitulo())));
            n.setEstadoEnvio("ENVIADO");
            n.setEnviadoEn(clock.instant());
        } catch (MailDeliveryException exception) {
            n.setEstadoEnvio("ERROR");
            n.setUltimoError(exception.getMessage());
        }
        notificaciones.save(n);
    }

    private ReglaCurso reglaDe(Matricula matricula) {
        return reglasCurso.findByCurso_Id(matricula.getCurso().getId()).orElseGet(ReglaCurso::new);
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
