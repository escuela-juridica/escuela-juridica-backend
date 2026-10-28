package pe.edu.utp.escuela.app.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AulaRespuesta;
import pe.edu.utp.escuela.app.dto.LeccionAulaRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloAulaRespuesta;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/** HU-022 — Ingresar y continuar un curso: resuelve el acceso efectivo de la matrícula del
 * alumno actual a un curso y construye el temario (módulos → lecciones) con el estado de cada
 * lección y el punto donde "Continuar" debe reabrir el aula.
 *
 * <p>El cálculo de "acceso efectivo" replica exactamente, a propósito, el de {@code
 * MatriculaServicio#respuesta(Matricula, String)}: es la definición oficial de acceso efectivo en
 * todo el proyecto y no debe divergir. */
@Service
@RequiredArgsConstructor
public class AulaServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MatriculaRepositorio matriculas;
    private final ModuloRepositorio modulos;
    private final LeccionRepositorio lecciones;
    private final ProgresoLeccionRepositorio progresoLecciones;
    private final MaterialLeccionRepositorio materiales;
    private final ReglaCursoRepositorio reglasCurso;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AulaRespuesta temario(Long cursoId) {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));

        verificarAcceso(matricula);

        List<Modulo> modulosCurso = modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(cursoId);
        List<Long> idsModulos = modulosCurso.stream().map(Modulo::getId).toList();
        List<Leccion> globalOrden = ordenGlobalDeLecciones(idsModulos, modulosCurso);

        Map<Long, Boolean> completadas = progresoLecciones.findByMatricula_Id(matricula.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLeccion().getId(), ProgresoLeccion::isCompletada, (a, b) -> a));

        boolean secuenciaObligatoria = reglasCurso.findByCurso_Id(cursoId)
                .map(ReglaCurso::isSecuenciaObligatoria).orElse(true);

        List<Long> idsLecciones = globalOrden.stream().map(Leccion::getId).toList();
        Set<Long> leccionesConGrabacion = idsLecciones.isEmpty() ? Set.of()
                : materiales.findByLeccion_IdInOrderByLeccion_IdAscOrdenAsc(idsLecciones).stream()
                        .filter(MaterialLeccion::isActivo)
                        .map(m -> m.getLeccion().getId())
                        .collect(Collectors.toSet());

        Instant ahora = clock.instant();
        Map<Long, List<LeccionAulaRespuesta>> leccionesPorModulo = new LinkedHashMap<>();
        Map<Long, LeccionAulaRespuesta> respuestaPorLeccion = new HashMap<>();
        Leccion previaObligatoria = null;
        for (Leccion leccion : globalOrden) {
            boolean completada = Boolean.TRUE.equals(completadas.get(leccion.getId()));
            boolean bloqueadaPorSecuencia = secuenciaObligatoria && previaObligatoria != null
                    && !Boolean.TRUE.equals(completadas.get(previaObligatoria.getId()));

            String estado;
            String motivoBloqueo;
            if (completada) {
                estado = "COMPLETADA";
                motivoBloqueo = null;
            } else if (bloqueadaPorSecuencia) {
                estado = "BLOQUEADA";
                motivoBloqueo = "Completa la lección anterior: " + previaObligatoria.getTitulo();
            } else {
                estado = "DISPONIBLE";
                motivoBloqueo = null;
            }

            String estadoSesion = null;
            boolean grabacionDisponible = false;
            if ("EN_VIVO".equals(leccion.getTipo())) {
                estadoSesion = estadoSesion(leccion, ahora);
                grabacionDisponible = "REALIZADA".equals(estadoSesion)
                        && leccionesConGrabacion.contains(leccion.getId());
            }

            LeccionAulaRespuesta respuesta = new LeccionAulaRespuesta(
                    leccion.getId(), leccion.getTitulo(), leccion.getTipo(), leccion.getOrden(),
                    leccion.isEsObligatoria(), estado, motivoBloqueo,
                    leccion.getFechaHoraInicio(), leccion.getFechaHoraFin(), estadoSesion, grabacionDisponible);
            leccionesPorModulo.computeIfAbsent(leccion.getModulo().getId(), k -> new ArrayList<>()).add(respuesta);
            respuestaPorLeccion.put(leccion.getId(), respuesta);

            if (leccion.isEsObligatoria() && !"CANCELADA".equals(leccion.getEstado())) {
                previaObligatoria = leccion;
            }
        }

        List<ModuloAulaRespuesta> modulosRespuesta = new ArrayList<>();
        for (Modulo modulo : modulosCurso) {
            modulosRespuesta.add(new ModuloAulaRespuesta(modulo.getId(), modulo.getTitulo(), modulo.getOrden(),
                    leccionesPorModulo.getOrDefault(modulo.getId(), List.of())));
        }

        List<Leccion> obligatorias = globalOrden.stream()
                .filter(l -> l.isEsObligatoria() && !"CANCELADA".equals(l.getEstado())).toList();
        List<Leccion> baseParaPorcentaje = obligatorias.isEmpty() ? globalOrden : obligatorias;
        long total = baseParaPorcentaje.size();
        long hechas = baseParaPorcentaje.stream()
                .filter(l -> Boolean.TRUE.equals(completadas.get(l.getId()))).count();
        int porcentaje = total == 0 ? 0 : (int) Math.round(hechas * 100.0 / total);

        Long ultimaLeccionIdValida = ultimaLeccionValida(baseParaPorcentaje, respuestaPorLeccion);

        return new AulaRespuesta(matricula.getId(), matricula.getCurso().getId(), matricula.getCurso().getTitulo(),
                matricula.getCurso().getModalidad(), porcentaje, modulosRespuesta, ultimaLeccionIdValida);
    }

    /** HU-024 — Aplica del lado servidor la secuencia obligatoria que {@link #temario} solo refleja
     * en pantalla: devuelve el motivo si la lección todavía no puede trabajarse (hay una lección
     * obligatoria previa, no cancelada, sin completar), o {@code null} si está libre o ya
     * completada. Usa el mismo orden global y la misma regla de "previa obligatoria" que
     * {@code temario}, para que ambos nunca discrepen. */
    @Transactional(readOnly = true)
    public String motivoBloqueoPorSecuencia(Matricula matricula, Leccion leccion) {
        Long cursoId = leccion.getModulo().getCurso().getId();
        boolean secuenciaObligatoria = reglasCurso.findByCurso_Id(cursoId)
                .map(ReglaCurso::isSecuenciaObligatoria).orElse(true);
        if (!secuenciaObligatoria) {
            return null;
        }
        Map<Long, Boolean> completadas = progresoLecciones.findByMatricula_Id(matricula.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLeccion().getId(), ProgresoLeccion::isCompletada, (a, b) -> a));
        if (Boolean.TRUE.equals(completadas.get(leccion.getId()))) {
            return null;
        }

        List<Modulo> modulosCurso = modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(cursoId);
        List<Long> idsModulos = modulosCurso.stream().map(Modulo::getId).toList();
        Leccion previaObligatoria = null;
        for (Leccion candidata : ordenGlobalDeLecciones(idsModulos, modulosCurso)) {
            if (candidata.getId().equals(leccion.getId())) {
                boolean bloqueada = previaObligatoria != null
                        && !Boolean.TRUE.equals(completadas.get(previaObligatoria.getId()));
                return bloqueada ? "Completa la lección anterior: " + previaObligatoria.getTitulo() : null;
            }
            if (candidata.isEsObligatoria() && !"CANCELADA".equals(candidata.getEstado())) {
                previaObligatoria = candidata;
            }
        }
        return null;
    }

    /** Construye el orden global módulo→lección (por el orden de presentación de cada módulo y,
     * dentro de él, el de la lección), ya que los repositorios existentes solo ordenan por el id
     * interno del módulo, no por su campo "orden" visible. */
    private List<Leccion> ordenGlobalDeLecciones(List<Long> idsModulos, List<Modulo> modulosCurso) {
        if (idsModulos.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Leccion>> porModulo = lecciones.buscarActivasDeModulos(idsModulos).stream()
                .collect(Collectors.groupingBy(l -> l.getModulo().getId()));
        List<Leccion> global = new ArrayList<>();
        for (Modulo modulo : modulosCurso) {
            global.addAll(porModulo.getOrDefault(modulo.getId(), List.of()));
        }
        return global;
    }

    private String estadoSesion(Leccion leccion, Instant ahora) {
        if ("CANCELADA".equals(leccion.getEstado())) {
            return "CANCELADA";
        }
        if (leccion.getFechaHoraInicio() == null || leccion.getFechaHoraFin() == null) {
            // Sesión EN_VIVO aún sin programar (HU-012 todavía no le asignó fecha): se trata como
            // futura, a falta de un estado más específico en el enunciado.
            return "FUTURA";
        }
        if (ahora.isBefore(leccion.getFechaHoraInicio())) {
            return "FUTURA";
        }
        if (!ahora.isAfter(leccion.getFechaHoraFin())) {
            return "EN_VENTANA";
        }
        return "REALIZADA";
    }

    /** La primera lección (de la base considerada: obligatorias, o todas si no hay obligatorias)
     * que esté DISPONIBLE marca el punto de continuación; si todas están completadas, se usa la
     * última para reabrir el final del curso en vez de no abrir nada. */
    private Long ultimaLeccionValida(List<Leccion> base, Map<Long, LeccionAulaRespuesta> respuestaPorLeccion) {
        Leccion ultimaCompletada = null;
        for (Leccion leccion : base) {
            LeccionAulaRespuesta r = respuestaPorLeccion.get(leccion.getId());
            if ("DISPONIBLE".equals(r.estado())) {
                return leccion.getId();
            }
            if ("COMPLETADA".equals(r.estado())) {
                ultimaCompletada = leccion;
            }
        }
        return ultimaCompletada != null ? ultimaCompletada.getId() : null;
    }

    /** Replica MatriculaServicio#respuesta(...) para el cálculo de acceso efectivo, y añade el
     * caso de HU-022: un curso CANCELADO conserva el acceso de lectura de quien ya tenía
     * matrícula (no cancelada individualmente) antes de la cancelación. Como MatriculaServicio
     * solo permite crear/activar matrículas mientras el curso está PUBLICADO o EN_CURSO, cualquier
     * matrícula no cancelada individualmente que exista sobre un curso ya CANCELADO solo pudo
     * haberse aprobado antes de esa cancelación: no hace falta (ni hay cómo, sin una historia que
     * registre el instante exacto de cancelación) distinguir más. */
    private void verificarAcceso(Matricula m) {
        Instant ahora = clock.instant();
        boolean vigente = m.getFechaVencimiento() == null || !ahora.isAfter(m.getFechaVencimiento());
        boolean inicio = m.getCurso().getFechaInicio() == null
                || !LocalDate.now(clock.withZone(LIMA)).isBefore(m.getCurso().getFechaInicio());
        boolean cuentaHabilitada = m.getUsuario().isActivo() && m.getUsuario().getCorreoVerificadoEn() != null
                && !m.getUsuario().isRequiereCambioContrasena();
        boolean cancelada = "CANCELADA".equals(m.getEstado());
        boolean efectivo = !cancelada && "ACTIVA".equals(m.getEstado()) && vigente && inicio && cuentaHabilitada;
        if (efectivo) {
            return;
        }

        boolean cursoCancelado = "CANCELADO".equals(m.getCurso().getEstadoCurso().getCodigo());
        boolean accesoLecturaPorCancelacion = cursoCancelado && !cancelada && cuentaHabilitada;
        if (accesoLecturaPorCancelacion) {
            return;
        }

        String mensaje = cancelada
                ? "Tu matrícula fue cancelada" + (esVacio(m.getMotivoCancelacion()) ? "."
                        : ": " + m.getMotivoCancelacion())
                : !cuentaHabilitada ? "Completa la habilitación de tu cuenta para continuar."
                        : !inicio ? "El curso aún no inicia."
                                : !vigente || "VENCIDA".equals(m.getEstado()) ? "El acceso venció."
                                        : "El acceso no está disponible.";
        throw new BusinessValidationException(mensaje);
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
