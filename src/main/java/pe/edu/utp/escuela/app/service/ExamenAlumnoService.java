package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ExamenFichaResponse;
import pe.edu.utp.escuela.app.dto.GuardarRespuestaRequest;
import pe.edu.utp.escuela.app.dto.IntentoResponse;
import pe.edu.utp.escuela.app.dto.IntentoResumenResponse;
import pe.edu.utp.escuela.app.dto.OpcionIntentoResponse;
import pe.edu.utp.escuela.app.dto.PreguntaIntentoResponse;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.IntentoExamen;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.OpcionPregunta;
import pe.edu.utp.escuela.app.entity.Pregunta;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.RespuestaIntento;
import pe.edu.utp.escuela.app.entity.RevisionRespuesta;
import pe.edu.utp.escuela.app.event.ReevaluarCertificacionEvent;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.ExamenRepository;
import pe.edu.utp.escuela.app.repository.IntentoExamenRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.OpcionPreguntaRepository;
import pe.edu.utp.escuela.app.repository.PreguntaRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;
import pe.edu.utp.escuela.app.repository.RespuestaIntentoRepository;
import pe.edu.utp.escuela.app.repository.RevisionRespuestaRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.EstadoExamen;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamenAlumnoService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final BigDecimal ESCALA = BigDecimal.valueOf(20);

    private final MatriculaRepository matriculas;
    private final ExamenRepository examenes;
    private final PreguntaRepository preguntas;
    private final OpcionPreguntaRepository opciones;
    private final IntentoExamenRepository intentos;
    private final RespuestaIntentoRepository respuestas;
    private final ReglaCursoRepository reglasCurso;
    private final RevisionRespuestaRepository revisiones;
    private final RutaAcademicaService rutaAcademica;
    private final ApplicationEventPublisher eventos;
    private final CurrentUserService actual;
    private final Clock clock;

    private record Contexto(Examen examen, Matricula matricula, EstadoExamen estado, boolean soloLectura) {
    }

    @Transactional(noRollbackFor = BusinessValidationException.class)
    public ExamenFichaResponse ficha(Long examenId) {
        Contexto ctx = contexto(examenId);
        Examen examen = ctx.examen();
        EstadoExamen estado = ctx.estado();

        List<Pregunta> activas = preguntasActivas(examen.getId());
        BigDecimal total = activas.stream().map(Pregunta::getPuntaje).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean abierta = activas.stream().anyMatch(p -> "RESPUESTA_ABIERTA".equals(p.getTipo()));

        String motivo;
        boolean puedeIniciar;
        if (ctx.soloLectura()) {
            motivo = "El curso fue cancelado: puedes consultar el examen, pero ya no puedes rendirlo.";
            puedeIniciar = false;
        } else if (estado.enProgreso() != null) {
            motivo = null;
            puedeIniciar = false;
        } else if (activas.isEmpty()) {
            motivo = "Este examen aún no tiene preguntas.";
            puedeIniciar = false;
        } else {
            motivo = estado.motivoNoPuedeIniciar();
            puedeIniciar = motivo == null;
        }

        ReglaCurso regla = reglaDe(ctx.matricula());
        List<IntentoResumenResponse> historial = estado.intentos().stream()
                .map(i -> new IntentoResumenResponse(i.getId(), i.getNumeroIntento(), i.getEstado(), i.getNota(),
                        i.getAprobado(), i.getIniciadoEn(), i.getEnviadoEn(), limiteRevision(i, examen)))
                .toList();

        return new ExamenFichaResponse(examen.getId(), examen.getCurso().getId(), examen.getTitulo(),
                examen.getDescripcion(), examen.getTipo(), examen.getFinalidad(),
                examen.getModulo() == null ? null : examen.getModulo().getTitulo(), activas.size(), total, abierta,
                examen.getTiempoLimiteMinutos(), estado.permitidos(), estado.usados(), estado.restantes(),
                examen.getMostrarRespuestas(), regla.getNotaMinima(), estado.esCalificado(),
                examen.isBloqueaSiguienteModulo(), estado.motivoNoHabilitado() == null, puedeIniciar, motivo,
                estado.agotadoSinAprobar(), ctx.soloLectura(),
                estado.enProgreso() == null ? null : estado.enProgreso().getId(),
                estado.aprobado(), estado.mejorNota(), examen.getModoNavegacion(),
                examen.isPermiteOmitirPreguntas(), regla.getCriterioNota(), historial);
    }

    @Transactional(noRollbackFor = BusinessValidationException.class)
    public IntentoResponse iniciar(Long examenId) {
        Contexto ctx = contexto(examenId);
        if (ctx.soloLectura()) {
            throw new BusinessValidationException(
                    "El curso fue cancelado: puedes consultar el examen, pero ya no puedes rendirlo.");
        }
        EstadoExamen estado = ctx.estado();
        if (estado.enProgreso() != null) {
            return construir(estado.enProgreso(), ctx.matricula());
        }
        String motivo = estado.motivoNoPuedeIniciar();
        if (motivo != null) {
            throw new BusinessValidationException(motivo);
        }
        List<Pregunta> activas = preguntasActivas(ctx.examen().getId());
        if (activas.isEmpty()) {
            throw new BusinessValidationException("Este examen aún no tiene preguntas.");
        }

        Instant ahora = clock.instant();
        IntentoExamen nuevo = new IntentoExamen();
        nuevo.setMatricula(ctx.matricula());
        nuevo.setExamen(ctx.examen());
        nuevo.setNumeroIntento(estado.intentos().stream().mapToInt(IntentoExamen::getNumeroIntento).max().orElse(0) + 1);
        nuevo.setEstado(IntentoExamen.EN_PROGRESO);
        nuevo.setPuntajeTotal(activas.stream().map(Pregunta::getPuntaje).reduce(BigDecimal.ZERO, BigDecimal::add));
        nuevo.setIniciadoEn(ahora);
        if (ctx.examen().getTiempoLimiteMinutos() != null) {
            nuevo.setExpiraEn(ahora.plus(ctx.examen().getTiempoLimiteMinutos(), ChronoUnit.MINUTES));
        }
        intentos.save(nuevo);
        return construir(nuevo, ctx.matricula());
    }

    @Transactional(noRollbackFor = BusinessValidationException.class)
    public IntentoResponse intento(Long intentoId) {
        IntentoExamen intento = intentoDelAlumno(intentoId);
        cerrarSiVencido(intento);
        return construir(intento, intento.getMatricula());
    }

    @Transactional(noRollbackFor = BusinessValidationException.class)
    public void guardarRespuesta(Long intentoId, Long preguntaId, GuardarRespuestaRequest peticion) {
        IntentoExamen intento = intentoDelAlumno(intentoId);
        if (cerrarSiVencido(intento)) {
            throw new BusinessValidationException(
                    "El tiempo del intento terminó: se envió automáticamente con lo que tenías respondido.");
        }
        if (!intento.estaEnProgreso()) {
            throw new BusinessValidationException("Este intento ya fue enviado y no admite cambios.");
        }
        exigirEscritura(intento.getMatricula());

        Pregunta pregunta = preguntas.findById(preguntaId)
                .filter(p -> p.isActivo() && p.getExamen().getId().equals(intento.getExamen().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("La pregunta no pertenece a este examen."));

        RespuestaIntento respuesta = respuestas.findByIntentoExamen_IdAndPregunta_Id(intento.getId(), preguntaId)
                .orElseGet(() -> {
                    RespuestaIntento nueva = new RespuestaIntento();
                    nueva.setIntentoExamen(intento);
                    nueva.setPregunta(pregunta);
                    return nueva;
                });

        if ("RESPUESTA_ABIERTA".equals(pregunta.getTipo())) {
            String texto = peticion.texto() == null ? null : peticion.texto().strip();
            respuesta.setTextoRespuesta(texto == null || texto.isEmpty() ? null : texto);
            respuesta.getOpciones().clear();
        } else {
            List<Long> ids = peticion.opcionesIds() == null ? List.of() : peticion.opcionesIds().stream().distinct().toList();
            boolean unica = !"SELECCION_MULTIPLE".equals(pregunta.getTipo());
            if (unica && ids.size() > 1) {
                throw new BusinessValidationException("Esta pregunta admite una sola alternativa.");
            }
            Map<Long, OpcionPregunta> validas = opciones.findByPregunta_IdOrderByOrdenAsc(preguntaId).stream()
                    .filter(OpcionPregunta::isActivo).collect(Collectors.toMap(OpcionPregunta::getId, o -> o));
            Set<OpcionPregunta> elegidas = new HashSet<>();
            for (Long id : ids) {
                OpcionPregunta opcion = validas.get(id);
                if (opcion == null) {
                    throw new BusinessValidationException("Una alternativa no pertenece a esta pregunta.");
                }
                elegidas.add(opcion);
            }
            respuesta.getOpciones().clear();
            respuesta.getOpciones().addAll(elegidas);
            respuesta.setTextoRespuesta(null);
        }
        respuestas.save(respuesta);
    }

    @Transactional(noRollbackFor = BusinessValidationException.class)
    public IntentoResponse enviar(Long intentoId) {
        IntentoExamen intento = intentoDelAlumno(intentoId);
        if (!cerrarSiVencido(intento) && intento.estaEnProgreso()) {
            exigirEscritura(intento.getMatricula());
            exigirTodasRespondidas(intento);
            calificar(intento, clock.instant());
        }
        return construir(intento, intento.getMatricula());
    }

    private void exigirTodasRespondidas(IntentoExamen intento) {
        Examen examen = intento.getExamen();
        if (examen.isPermiteOmitirPreguntas()) {
            return;
        }
        Map<Long, RespuestaIntento> guardadas = respuestas.findByIntentoExamen_Id(intento.getId()).stream()
                .collect(Collectors.toMap(r -> r.getPregunta().getId(), r -> r));
        long faltan = preguntasActivas(examen.getId()).stream()
                .filter(pregunta -> !estaRespondida(pregunta, guardadas.get(pregunta.getId())))
                .count();
        if (faltan > 0) {
            throw new BusinessValidationException((faltan == 1 ? "Te falta responder 1 pregunta. "
                    : "Te faltan por responder " + faltan + " preguntas. ")
                    + "Responde todas las preguntas antes de enviar.");
        }
    }

    private static boolean estaRespondida(Pregunta pregunta, RespuestaIntento respuesta) {
        if (respuesta == null) {
            return false;
        }
        if ("RESPUESTA_ABIERTA".equals(pregunta.getTipo())) {
            String texto = respuesta.getTextoRespuesta();
            return texto != null && !texto.isBlank();
        }
        return !respuesta.getOpciones().isEmpty();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 50_000)
    @Transactional
    public void cerrarVencidos() {
        Instant ahora = clock.instant();
        for (IntentoExamen pendiente : intentos.findByEstadoAndExpiraEnBefore(IntentoExamen.EN_PROGRESO, ahora)) {
            try {
                intentos.buscarConBloqueo(pendiente.getId()).ifPresent(this::cerrarSiVencido);
            } catch (RuntimeException e) {
                log.warn("No se pudo cerrar por tiempo el intento {}", pendiente.getId(), e);
            }
        }
    }

    private boolean cerrarSiVencido(IntentoExamen intento) {
        if (!intento.estaEnProgreso() || intento.getExpiraEn() == null || clock.instant().isBefore(intento.getExpiraEn())) {
            return false;
        }
        calificar(intento, intento.getExpiraEn());
        return true;
    }

    private void calificar(IntentoExamen intento, Instant momentoEnvio) {
        Examen examen = intento.getExamen();
        List<Pregunta> activas = preguntasActivas(examen.getId());
        Map<Long, List<OpcionPregunta>> opcionesPorPregunta = opciones
                .findByPregunta_IdInOrderByPregunta_IdAscOrdenAsc(activas.stream().map(Pregunta::getId).toList()).stream()
                .filter(OpcionPregunta::isActivo)
                .collect(Collectors.groupingBy(o -> o.getPregunta().getId()));
        Map<Long, RespuestaIntento> guardadas = respuestas.findByIntentoExamen_Id(intento.getId()).stream()
                .collect(Collectors.toMap(r -> r.getPregunta().getId(), r -> r));

        BigDecimal obtenido = BigDecimal.ZERO;
        boolean hayAbiertasPendientes = false;
        for (Pregunta pregunta : activas) {
            RespuestaIntento respuesta = guardadas.get(pregunta.getId());
            if (respuesta == null) {
                respuesta = new RespuestaIntento();
                respuesta.setIntentoExamen(intento);
                respuesta.setPregunta(pregunta);
            }
            if ("RESPUESTA_ABIERTA".equals(pregunta.getTipo())) {
                String texto = respuesta.getTextoRespuesta();
                if (texto == null || texto.isBlank()) {
                    respuesta.setPuntajeDefinitivo(BigDecimal.ZERO);
                    respuesta.setEstadoRevision(RespuestaIntento.NO_APLICA);
                } else {
                    respuesta.setEstadoRevision(RespuestaIntento.PENDIENTE);
                    respuesta.setFechaLimiteRevision(momentoEnvio.plus(examen.getDiasRevision(), ChronoUnit.DAYS));
                    hayAbiertasPendientes = true;
                }
            } else {
                BigDecimal ganado = acierta(pregunta, respuesta, opcionesPorPregunta.getOrDefault(pregunta.getId(), List.of()))
                        ? pregunta.getPuntaje() : BigDecimal.ZERO;
                respuesta.setPuntajeAutomatico(ganado);
                respuesta.setPuntajeDefinitivo(ganado);
                respuesta.setEstadoRevision(RespuestaIntento.NO_APLICA);
                obtenido = obtenido.add(ganado);
            }
            respuestas.save(respuesta);
        }

        intento.setEnviadoEn(momentoEnvio);
        if (hayAbiertasPendientes) {
            intento.setEstado(IntentoExamen.PENDIENTE_REVISION);
        } else {
            BigDecimal nota = calcularNota(obtenido, intento.getPuntajeTotal());
            intento.setEstado(IntentoExamen.CALIFICADO);
            intento.setPuntajeObtenido(obtenido);
            intento.setNota(nota);
            intento.setAprobado(nota.compareTo(reglaDe(intento.getMatricula()).getNotaMinima()) >= 0);
            intento.setCalificadoEn(momentoEnvio);
        }
        intentos.save(intento);
        eventos.publishEvent(new ReevaluarCertificacionEvent(intento.getMatricula().getId()));
    }

    public static BigDecimal calcularNota(BigDecimal obtenido, BigDecimal total) {
        return obtenido.multiply(ESCALA).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static boolean acierta(Pregunta pregunta, RespuestaIntento respuesta, List<OpcionPregunta> disponibles) {
        Set<Long> correctas = disponibles.stream().filter(OpcionPregunta::isEsCorrecta)
                .map(OpcionPregunta::getId).collect(Collectors.toSet());
        Set<Long> marcadas = respuesta.getOpciones().stream().map(OpcionPregunta::getId).collect(Collectors.toSet());
        return !correctas.isEmpty() && correctas.equals(marcadas);
    }

    private IntentoResponse construir(IntentoExamen intento, Matricula matricula) {
        Examen examen = intento.getExamen();
        Instant ahora = clock.instant();
        boolean enProgreso = intento.estaEnProgreso();

        List<Pregunta> activas = new ArrayList<>(preguntasActivas(examen.getId()));
        if (examen.isBarajarPreguntas()) {
            Collections.shuffle(activas, new Random(intento.getId()));
        }
        Map<Long, List<OpcionPregunta>> opcionesPorPregunta = opciones
                .findByPregunta_IdInOrderByPregunta_IdAscOrdenAsc(activas.stream().map(Pregunta::getId).toList()).stream()
                .filter(OpcionPregunta::isActivo)
                .collect(Collectors.groupingBy(o -> o.getPregunta().getId()));
        Map<Long, RespuestaIntento> guardadas = respuestas.findByIntentoExamen_Id(intento.getId()).stream()
                .collect(Collectors.toMap(r -> r.getPregunta().getId(), r -> r));

        boolean visibles = !enProgreso && respuestasVisibles(examen, matricula);
        Map<Long, RevisionRespuesta> revisadas = enProgreso ? Map.of() : revisiones
                .findByRespuestaIntento_IdInOrderByRevisadoEnDesc(
                        guardadas.values().stream().map(RespuestaIntento::getId).toList())
                .stream().collect(Collectors.toMap(r -> r.getRespuestaIntento().getId(), r -> r, (a, b) -> a));
        List<PreguntaIntentoResponse> filas = new ArrayList<>();
        int posicion = 1;
        for (Pregunta pregunta : activas) {
            List<OpcionPregunta> delaPregunta = new ArrayList<>(opcionesPorPregunta.getOrDefault(pregunta.getId(), List.of()));
            if (examen.isBarajarOpciones()) {
                Collections.shuffle(delaPregunta, new Random(intento.getId() * 1_000_003L + pregunta.getId()));
            }
            RespuestaIntento respuesta = guardadas.get(pregunta.getId());
            List<Long> seleccionadas = respuesta == null ? List.of()
                    : respuesta.getOpciones().stream().map(OpcionPregunta::getId).sorted().toList();
            boolean abierta = "RESPUESTA_ABIERTA".equals(pregunta.getTipo());
            String texto = respuesta == null ? null : respuesta.getTextoRespuesta();
            boolean respondida = abierta ? texto != null && !texto.isBlank() : !seleccionadas.isEmpty();

            BigDecimal puntaje = null;
            Boolean correcta = null;
            String estadoRevision = null;
            String observacion = null;
            if (!enProgreso && respuesta != null) {
                estadoRevision = respuesta.getEstadoRevision();
                if (visibles) {
                    puntaje = respuesta.getPuntajeDefinitivo();
                }
                RevisionRespuesta revision = revisadas.get(respuesta.getId());
                if (abierta && revision != null) {
                    puntaje = revision.getPuntajeAsignado();
                    observacion = revision.getObservacion();
                }
                if (visibles && !abierta) {
                    correcta = pregunta.getPuntaje().compareTo(respuesta.getPuntajeAutomatico() == null
                            ? BigDecimal.ZERO : respuesta.getPuntajeAutomatico()) == 0;
                }
            }
            List<OpcionIntentoResponse> opcionesDto = delaPregunta.stream()
                    .map(o -> new OpcionIntentoResponse(o.getId(), o.getTexto(), visibles ? o.isEsCorrecta() : null))
                    .toList();
            filas.add(new PreguntaIntentoResponse(pregunta.getId(), pregunta.getTipo(), pregunta.getEnunciado(),
                    pregunta.getPuntaje(), posicion++, opcionesDto, seleccionadas, texto, respondida, puntaje,
                    correcta, estadoRevision, observacion));
        }

        Long restantes = enProgreso && intento.getExpiraEn() != null
                ? Math.max(0, Duration.between(ahora, intento.getExpiraEn()).getSeconds()) : null;
        boolean porTiempo = intento.getExpiraEn() != null && intento.getEnviadoEn() != null
                && !intento.getEnviadoEn().isBefore(intento.getExpiraEn());
        return new IntentoResponse(intento.getId(), examen.getId(), examen.getCurso().getId(), examen.getTitulo(),
                examen.getTipo(), intento.getNumeroIntento(), intento.getEstado(), intento.getIniciadoEn(),
                intento.getExpiraEn(), restantes, ahora, intento.getPuntajeTotal(), intento.getPuntajeObtenido(),
                intento.getNota(), intento.getAprobado(), reglaDe(matricula).getNotaMinima(), intento.getEnviadoEn(),
                porTiempo, limiteRevision(intento, examen), visibles, "CALIFICADO".equals(examen.getTipo()),
                examen.getModoNavegacion(), examen.isPermiteOmitirPreguntas(), filas);
    }

    private boolean respuestasVisibles(Examen examen, Matricula matricula) {
        RutaAcademica ruta = rutaAcademica.calcular(matricula);
        EstadoExamen estado = ruta.estadoExamenes().get(examen.getId());
        if (estado == null) {
            return false;
        }
        return switch (examen.getMostrarRespuestas()) {
            case "AL_APROBAR" -> estado.aprobado();
            case "AL_AGOTAR" -> estado.agotado();
            default -> false;
        };
    }

    private static Instant limiteRevision(IntentoExamen intento, Examen examen) {
        return intento.estaPendienteRevision() && intento.getEnviadoEn() != null
                ? intento.getEnviadoEn().plus(examen.getDiasRevision(), ChronoUnit.DAYS) : null;
    }

    private Contexto contexto(Long examenId) {
        exigirAlumno();
        Examen examen = examenes.findById(examenId).filter(Examen::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("El examen no existe."));
        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), examen.getCurso().getId())
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));
        boolean soloLectura = verificarAcceso(matricula);

        for (IntentoExamen intento : intentos.findByMatricula_IdAndExamen_IdOrderByNumeroIntentoAsc(matricula.getId(), examenId)) {
            if (intento.estaEnProgreso() && intento.getExpiraEn() != null && !clock.instant().isBefore(intento.getExpiraEn())) {
                intentos.buscarConBloqueo(intento.getId()).ifPresent(this::cerrarSiVencido);
            }
        }
        RutaAcademica ruta = rutaAcademica.calcular(matricula);
        EstadoExamen estado = ruta.estadoExamenes().get(examenId);
        if (estado == null) {
            throw new ResourceNotFoundException("El examen no existe.");
        }
        return new Contexto(examen, matricula, estado, soloLectura);
    }

    private IntentoExamen intentoDelAlumno(Long intentoId) {
        exigirAlumno();
        IntentoExamen intento = intentos.buscarConBloqueo(intentoId)
                .filter(i -> i.getMatricula().getUsuario().getId().equals(actual.get().userId()))
                .orElseThrow(() -> new ResourceNotFoundException("El intento no existe."));
        return intento;
    }

    private void exigirAlumno() {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
    }

    private List<Pregunta> preguntasActivas(Long examenId) {
        return preguntas.findByExamen_IdOrderByOrdenAsc(examenId).stream().filter(Pregunta::isActivo).toList();
    }

    private ReglaCurso reglaDe(Matricula matricula) {
        return reglasCurso.findByCurso_Id(matricula.getCurso().getId()).orElseGet(ReglaCurso::new);
    }

    private void exigirEscritura(Matricula matricula) {
        if (verificarAcceso(matricula)) {
            throw new BusinessValidationException(
                    "El curso fue cancelado: puedes consultar el examen, pero ya no puedes rendirlo.");
        }
    }

    private boolean verificarAcceso(Matricula m) {
        Instant ahora = clock.instant();
        boolean vigente = m.getFechaVencimiento() == null || !ahora.isAfter(m.getFechaVencimiento());
        boolean inicio = m.getCurso().getFechaInicio() == null
                || !LocalDate.now(clock.withZone(LIMA)).isBefore(m.getCurso().getFechaInicio());
        boolean cuentaHabilitada = m.getUsuario().isActivo() && m.getUsuario().getCorreoVerificadoEn() != null
                && !m.getUsuario().isRequiereCambioContrasena();
        boolean cancelada = "CANCELADA".equals(m.getEstado());
        boolean cursoCancelado = "CANCELADO".equals(m.getCurso().getEstadoCurso().getCodigo());
        boolean efectivo = !cancelada && "ACTIVA".equals(m.getEstado()) && vigente && inicio && cuentaHabilitada;
        if (efectivo) {
            return cursoCancelado;
        }
        if (AccesoPorCancelacion.permiteLectura(m, cuentaHabilitada, LIMA)) {
            return true;
        }
        String mensaje = cancelada
                ? "Tu matrícula fue cancelada" + (m.getMotivoCancelacion() == null || m.getMotivoCancelacion().isBlank()
                        ? "." : ": " + m.getMotivoCancelacion())
                : !cuentaHabilitada ? "Completa la habilitación de tu cuenta para continuar."
                        : cursoCancelado ? AccesoPorCancelacion.MENSAJE_SIN_CONTENIDO
                        : !inicio ? "El curso aún no inicia."
                                : !vigente || "VENCIDA".equals(m.getEstado()) ? "El acceso venció."
                                        : "El acceso no está disponible.";
        throw new BusinessValidationException(mensaje);
    }
}
