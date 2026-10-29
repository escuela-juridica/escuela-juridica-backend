package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ExamenAulaResponse;
import pe.edu.utp.escuela.app.entity.ExcepcionAcademica;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.IntentoExamen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.ExamenRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.IntentoExamenRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.ModuloRepository;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;

/**
 * HU-025 — Ruta académica: única fuente de verdad del orden del curso y de qué lecciones están
 * bloqueadas y por qué. La consumen el aula (para pintar el temario) y el registro de progreso
 * (para no dejar completar una lección bloqueada llamando a la API directo), de modo que el
 * índice, el botón "Siguiente" y el acceso directo respeten siempre el mismo orden y los mismos
 * bloqueos. Nada de esto se guarda como "módulo desbloqueado": se calcula en cada consulta desde
 * las reglas del curso y los resultados reales del alumno.
 *
 * <p>Con la secuencia activa una lección se bloquea por (1) una lección obligatoria previa, no
 * cancelada, sin completar, o (2) un examen calificado de módulo, configurado como bloqueante, sin
 * aprobar en un módulo anterior (si hay varios, todos deben aprobarse). Las prácticas y el examen
 * final nunca bloquean. Con la secuencia apagada nada bloquea. Una sesión CANCELADA queda libre.
 * Lo ya completado siempre queda accesible.
 *
 * <p>HU-028 — La misma ruta decide si un examen está habilitado y cuántos intentos le quedan al
 * alumno: el aula, la ficha del examen y el inicio del intento comparten este cálculo.
 */
@Service
@RequiredArgsConstructor
public class RutaAcademicaService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' h:mm a", Locale.forLanguageTag("es-PE"));

    private final ModuloRepository modulos;
    private final LeccionRepository lecciones;
    private final ProgresoLeccionRepository progresoLecciones;
    private final ReglaCursoRepository reglasCurso;
    private final ExamenRepository examenes;
    private final IntentoExamenRepository intentos;
    private final ExcepcionAcademicaRepository excepciones;
    private final CertificadoRepository certificados;
    private final Clock clock;

    /**
     * Situación de un alumno frente a un examen: intentos usados y permitidos, resultado y si
     * puede iniciar otro. {@code permitidos == null} significa intentos ilimitados.
     */
    public record EstadoExamen(
            Examen examen,
            List<IntentoExamen> intentos,
            int usados,
            Integer permitidos,
            IntentoExamen enProgreso,
            IntentoExamen pendienteRevision,
            boolean aprobado,
            BigDecimal mejorNota,
            boolean certificadoEmitido,
            String motivoNoHabilitado) {

        public boolean agotado() {
            return permitidos != null && usados >= permitidos;
        }

        public boolean esCalificado() {
            return "CALIFICADO".equals(examen.getTipo());
        }

        /** Texto que explica por qué no puede iniciar otro intento, o {@code null} si puede. */
        public String motivoNoPuedeIniciar() {
            if (motivoNoHabilitado != null) {
                return motivoNoHabilitado;
            }
            if (pendienteRevision != null) {
                return "Tu intento anterior está pendiente de revisión. Podrás rendir otro cuando termine.";
            }
            if (esCalificado() && certificadoEmitido) {
                return "Tu certificado ya fue emitido: este examen calificado no admite nuevos intentos.";
            }
            if (agotado()) {
                return aprobado
                        ? "Ya usaste todos los intentos de este examen."
                        : "Agotaste tus intentos sin aprobar: no puedes reintentar ni certificar. "
                                + "Administración debe otorgarte un intento adicional justificado para continuar.";
            }
            return null;
        }

        public boolean puedeIniciar() {
            return enProgreso == null && motivoNoPuedeIniciar() == null;
        }

        public boolean agotadoSinAprobar() {
            return agotado() && !aprobado;
        }

        public Integer restantes() {
            return permitidos == null ? null : Math.max(0, permitidos - usados);
        }

        /** PENDIENTE | EN_CURSO | EN_REVISION | APROBADO | NO_APROBADO */
        public String codigo() {
            if (enProgreso != null) {
                return "EN_CURSO";
            }
            if (aprobado) {
                return "APROBADO";
            }
            if (pendienteRevision != null) {
                return "EN_REVISION";
            }
            return intentos.isEmpty() ? "PENDIENTE" : "NO_APROBADO";
        }
    }

    /** Resultado del cálculo para una matrícula. */
    public record RutaAcademica(
            ReglaCurso regla,
            List<Modulo> modulos,
            Map<Long, List<Leccion>> leccionesPorModulo,
            List<Leccion> globalOrden,
            Map<Long, Boolean> completadas,
            Map<Long, String> bloqueos,
            Map<Long, List<ExamenAulaResponse>> examenesPorModulo,
            List<ExamenAulaResponse> examenesFinales,
            List<Examen> examenesCalificados,
            Set<Long> examenesAprobados,
            Map<Long, BigDecimal> mejoresNotas,
            Map<Long, EstadoExamen> estadoExamenes) {

        /** Motivo por el que la lección no puede trabajarse todavía, o {@code null} si está libre. */
        public String motivoBloqueo(Long leccionId) {
            return bloqueos.get(leccionId);
        }
    }

    @Transactional(readOnly = true)
    public RutaAcademica calcular(Matricula matricula) {
        Long cursoId = matricula.getCurso().getId();
        ReglaCurso regla = reglasCurso.findByCurso_Id(cursoId).orElseGet(ReglaCurso::new);

        List<Modulo> modulosCurso = modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(cursoId);
        List<Long> idsModulos = modulosCurso.stream().map(Modulo::getId).toList();
        Map<Long, List<Leccion>> porModulo = idsModulos.isEmpty() ? Map.of()
                : lecciones.buscarActivasDeModulos(idsModulos).stream()
                        .collect(Collectors.groupingBy(l -> l.getModulo().getId()));
        Map<Long, List<Leccion>> leccionesPorModulo = new LinkedHashMap<>();
        List<Leccion> global = new ArrayList<>();
        for (Modulo modulo : modulosCurso) {
            List<Leccion> delModulo = porModulo.getOrDefault(modulo.getId(), List.of());
            leccionesPorModulo.put(modulo.getId(), delModulo);
            global.addAll(delModulo);
        }

        Map<Long, Boolean> completadas = progresoLecciones.findByMatricula_Id(matricula.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLeccion().getId(), ProgresoLeccion::isCompletada, (a, b) -> a));

        List<Examen> activos = examenes.findByCurso_IdOrderByOrdenAsc(cursoId).stream()
                .filter(Examen::isActivo).toList();

        // HU-028: intentos de la matrícula agrupados por examen y excepciones de intento adicional.
        Map<Long, List<IntentoExamen>> intentosPorExamen = intentos
                .findByMatricula_IdOrderByIniciadoEnAsc(matricula.getId()).stream()
                .collect(Collectors.groupingBy(i -> i.getExamen().getId()));
        Map<Long, Long> extrasPorExamen = excepciones
                .findByMatricula_IdAndTipo(matricula.getId(), ExcepcionAcademica.REABRIR_INTENTO).stream()
                .filter(e -> e.getReferenciaId() != null)
                .collect(Collectors.groupingBy(ExcepcionAcademica::getReferenciaId, Collectors.counting()));
        boolean certificadoEmitido = certificados.findByLogro_Matricula_Id(matricula.getId())
                .filter(c -> "VIGENTE".equals(c.getEstado())).isPresent();
        Instant ahora = clock.instant();

        Map<Long, EstadoExamen> estados = new LinkedHashMap<>();
        for (Examen examen : activos) {
            String motivo = motivoNoHabilitado(examen, matricula, regla, leccionesPorModulo, completadas, ahora);
            estados.put(examen.getId(), estadoDe(examen, intentosPorExamen.getOrDefault(examen.getId(), List.of()),
                    extrasPorExamen.getOrDefault(examen.getId(), 0L).intValue(), certificadoEmitido, motivo));
        }

        Set<Long> aprobados = estados.values().stream().filter(EstadoExamen::aprobado)
                .map(e -> e.examen().getId()).collect(Collectors.toCollection(HashSet::new));
        Map<Long, BigDecimal> notas = new HashMap<>();
        estados.values().stream().filter(e -> e.mejorNota() != null)
                .forEach(e -> notas.put(e.examen().getId(), e.mejorNota()));

        Set<Long> idsModulosActivos = new HashSet<>(idsModulos);
        Map<Long, List<ExamenAulaResponse>> examenesPorModulo = new HashMap<>();
        List<ExamenAulaResponse> examenesFinales = new ArrayList<>();
        for (Examen examen : activos) {
            ExamenAulaResponse fila = filaAula(estados.get(examen.getId()));
            if (examen.getModulo() == null) {
                examenesFinales.add(fila);
            } else if (idsModulosActivos.contains(examen.getModulo().getId())) {
                examenesPorModulo.computeIfAbsent(examen.getModulo().getId(), k -> new ArrayList<>()).add(fila);
            }
        }

        Map<Long, String> bloqueos = new HashMap<>();
        if (regla.isSecuenciaObligatoria()) {
            Leccion previaObligatoria = null;
            List<String> examenesPendientes = new ArrayList<>();
            for (Modulo modulo : modulosCurso) {
                for (Leccion leccion : leccionesPorModulo.get(modulo.getId())) {
                    if (!Boolean.TRUE.equals(completadas.get(leccion.getId()))) {
                        if (previaObligatoria != null
                                && !Boolean.TRUE.equals(completadas.get(previaObligatoria.getId()))) {
                            bloqueos.put(leccion.getId(),
                                    "Completa la lección anterior: " + previaObligatoria.getTitulo());
                        } else if (!examenesPendientes.isEmpty()) {
                            bloqueos.put(leccion.getId(), motivoExamenes(examenesPendientes));
                        }
                    }
                    // Una sesión CANCELADA se excluye y libera la secuencia: no cuenta como previa.
                    if (leccion.isEsObligatoria() && !"CANCELADA".equals(leccion.getEstado())) {
                        previaObligatoria = leccion;
                    }
                }
                for (ExamenAulaResponse examen : examenesPorModulo.getOrDefault(modulo.getId(), List.of())) {
                    if (examen.bloqueaSiguienteModulo() && !examen.aprobado()) {
                        examenesPendientes.add(examen.titulo());
                    }
                }
            }
        }

        List<Examen> calificados = activos.stream().filter(e -> "CALIFICADO".equals(e.getTipo())).toList();
        return new RutaAcademica(regla, modulosCurso, leccionesPorModulo, global, completadas, bloqueos,
                examenesPorModulo, examenesFinales, calificados, aprobados, notas, estados);
    }

    private EstadoExamen estadoDe(Examen examen, List<IntentoExamen> delExamen, int extras,
            boolean certificadoEmitido, String motivoNoHabilitado) {
        List<IntentoExamen> ordenados = delExamen.stream()
                .sorted(Comparator.comparing(IntentoExamen::getNumeroIntento)).toList();
        IntentoExamen enProgreso = ordenados.stream().filter(IntentoExamen::estaEnProgreso).findFirst().orElse(null);
        IntentoExamen pendiente = ordenados.stream().filter(IntentoExamen::estaPendienteRevision).findFirst().orElse(null);
        boolean aprobado = ordenados.stream().anyMatch(i -> Boolean.TRUE.equals(i.getAprobado()));
        BigDecimal mejor = ordenados.stream()
                .filter(i -> IntentoExamen.CALIFICADO.equals(i.getEstado()) && i.getNota() != null)
                .map(IntentoExamen::getNota).max(Comparator.naturalOrder()).orElse(null);
        // La práctica siempre es ilimitada; un examen calificado sin máximo también.
        Integer permitidos = "PRACTICA".equals(examen.getTipo()) || examen.getMaximoIntentos() == null
                ? null : examen.getMaximoIntentos() + extras;
        return new EstadoExamen(examen, ordenados, ordenados.size(), permitidos, enProgreso, pendiente, aprobado,
                mejor, certificadoEmitido, motivoNoHabilitado);
    }

    private static ExamenAulaResponse filaAula(EstadoExamen e) {
        Examen examen = e.examen();
        // Fecha límite prometida para calificar: envío + días de revisión del examen (HU-029).
        Instant limite = e.pendienteRevision() == null || e.pendienteRevision().getEnviadoEn() == null ? null
                : e.pendienteRevision().getEnviadoEn().plus(examen.getDiasRevision(), java.time.temporal.ChronoUnit.DAYS);
        return new ExamenAulaResponse(examen.getId(), examen.getTitulo(), examen.getTipo(), examen.getFinalidad(),
                esBloqueante(examen), e.aprobado(), e.mejorNota(), e.codigo(), e.motivoNoHabilitado() == null,
                e.motivoNoHabilitado(), e.usados(), e.permitidos(), limite);
    }

    /**
     * Habilitación de un examen (HU-028): con secuencia, el de módulo abre al completar las
     * lecciones obligatorias de su módulo y el final al completar todos los módulos; sin secuencia
     * abre con la matrícula. En EN_VIVO / HIBRIDO una fecha configurada se SUMA al avance exigido.
     * Devuelve {@code null} si está habilitado.
     */
    private String motivoNoHabilitado(Examen examen, Matricula matricula, ReglaCurso regla,
            Map<Long, List<Leccion>> leccionesPorModulo, Map<Long, Boolean> completadas, Instant ahora) {
        if (regla.isSecuenciaObligatoria()) {
            if (examen.getModulo() != null) {
                boolean pendientes = leccionesPorModulo.getOrDefault(examen.getModulo().getId(), List.of()).stream()
                        .anyMatch(l -> exigible(l) && !Boolean.TRUE.equals(completadas.get(l.getId())));
                if (pendientes) {
                    return "Completa las lecciones del módulo «" + examen.getModulo().getTitulo()
                            + "» para rendir este examen.";
                }
            } else {
                boolean pendientes = leccionesPorModulo.values().stream().flatMap(List::stream)
                        .anyMatch(l -> exigible(l) && !Boolean.TRUE.equals(completadas.get(l.getId())));
                if (pendientes) {
                    return "Completa todos los módulos del curso para rendir el examen final.";
                }
            }
        }
        boolean exigeFecha = !"VIRTUAL".equals(matricula.getCurso().getModalidad())
                && examen.getFechaHabilitacion() != null;
        if (exigeFecha && ahora.isBefore(examen.getFechaHabilitacion())) {
            return "Este examen se habilita el "
                    + FECHA_HORA.format(examen.getFechaHabilitacion().atZone(LIMA)) + " (hora de Lima).";
        }
        return null;
    }

    private static boolean exigible(Leccion leccion) {
        return leccion.isEsObligatoria() && !"CANCELADA".equals(leccion.getEstado());
    }

    /** Solo un examen CALIFICADO de MÓDULO configurado como bloqueante frena el siguiente módulo;
     * la práctica y el examen final no, aunque tengan la bandera encendida. */
    private static boolean esBloqueante(Examen examen) {
        return examen.isBloqueaSiguienteModulo() && "CALIFICADO".equals(examen.getTipo())
                && "MODULO".equals(examen.getFinalidad()) && examen.getModulo() != null;
    }

    private static String motivoExamenes(List<String> titulos) {
        if (titulos.size() == 1) {
            return "Aprueba el examen «" + titulos.get(0) + "» para continuar.";
        }
        return "Aprueba estos exámenes para continuar: "
                + titulos.stream().map(t -> "«" + t + "»").collect(Collectors.joining(", ")) + ".";
    }
}
