package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AulaRespuesta;
import pe.edu.utp.escuela.app.dto.AvanceCertificadoRespuesta;
import pe.edu.utp.escuela.app.dto.CondicionAvanceRespuesta;
import pe.edu.utp.escuela.app.dto.LeccionAulaRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloAulaRespuesta;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.RutaAcademicaServicio.RutaAcademica;

/** HU-022 — Ingresar y continuar un curso: resuelve el acceso efectivo de la matrícula del
 * alumno actual a un curso y construye el temario (módulos → lecciones) con el estado de cada
 * lección y el punto donde "Continuar" debe reabrir el aula. HU-025 — el orden y los bloqueos
 * vienen de {@link RutaAcademicaServicio} y el aula suma los exámenes de cada módulo y el bloque
 * "Tu avance hacia el certificado".
 *
 * <p>El cálculo de "acceso efectivo" replica exactamente, a propósito, el de {@code
 * MatriculaServicio#respuesta(Matricula, String)}: es la definición oficial de acceso efectivo en
 * todo el proyecto y no debe divergir. */
@Service
@RequiredArgsConstructor
public class AulaServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MatriculaRepositorio matriculas;
    private final MaterialLeccionRepositorio materiales;
    private final RutaAcademicaServicio rutaAcademica;
    private final SesionAlumnoServicio sesionAlumno;
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

        RutaAcademica ruta = rutaAcademica.calcular(matricula);
        List<Leccion> globalOrden = ruta.globalOrden();
        Map<Long, Boolean> completadas = ruta.completadas();

        List<Long> idsLecciones = globalOrden.stream().map(Leccion::getId).toList();
        Set<Long> leccionesConGrabacion = idsLecciones.isEmpty() ? Set.of()
                : materiales.findByLeccion_IdInOrderByLeccion_IdAscOrdenAsc(idsLecciones).stream()
                        .filter(MaterialLeccion::isActivo)
                        .map(m -> m.getLeccion().getId())
                        .collect(Collectors.toSet());

        Instant ahora = clock.instant();
        Map<Long, LeccionAulaRespuesta> respuestaPorLeccion = new java.util.HashMap<>();
        for (Leccion leccion : globalOrden) {
            boolean completada = Boolean.TRUE.equals(completadas.get(leccion.getId()));
            String motivoBloqueo = completada ? null : ruta.motivoBloqueo(leccion.getId());
            String estado = completada ? "COMPLETADA" : motivoBloqueo != null ? "BLOQUEADA" : "DISPONIBLE";

            String estadoSesion = null;
            boolean grabacionDisponible = false;
            if ("EN_VIVO".equals(leccion.getTipo())) {
                estadoSesion = estadoSesion(leccion, ahora);
                grabacionDisponible = "REALIZADA".equals(estadoSesion)
                        && leccionesConGrabacion.contains(leccion.getId());
            }

            respuestaPorLeccion.put(leccion.getId(), new LeccionAulaRespuesta(
                    leccion.getId(), leccion.getTitulo(), leccion.getTipo(), leccion.getOrden(),
                    leccion.isEsObligatoria(), estado, motivoBloqueo,
                    leccion.getFechaHoraInicio(), leccion.getFechaHoraFin(), estadoSesion, grabacionDisponible));
        }

        List<ModuloAulaRespuesta> modulosRespuesta = new ArrayList<>();
        for (Modulo modulo : ruta.modulos()) {
            List<LeccionAulaRespuesta> delModulo = ruta.leccionesPorModulo().get(modulo.getId()).stream()
                    .map(l -> respuestaPorLeccion.get(l.getId())).toList();
            modulosRespuesta.add(new ModuloAulaRespuesta(modulo.getId(), modulo.getTitulo(), modulo.getOrden(),
                    delModulo, ruta.examenesPorModulo().getOrDefault(modulo.getId(), List.of())));
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
                matricula.getCurso().getModalidad(), porcentaje, modulosRespuesta, ultimaLeccionIdValida,
                avanceCertificado(matricula, ruta, porcentaje));
    }

    /** HU-025 — "Tu avance hacia el certificado": solo las condiciones que el curso tiene activas,
     * cada una con su valor real y su meta, y una frase que dice qué falta. */
    private AvanceCertificadoRespuesta avanceCertificado(Matricula matricula, RutaAcademica ruta, int porcentaje) {
        ReglaCurso regla = ruta.regla();
        List<CondicionAvanceRespuesta> condiciones = new ArrayList<>();
        List<String> faltantes = new ArrayList<>();

        if (regla.isRequiereProgreso()) {
            boolean cumplida = BigDecimal.valueOf(porcentaje).compareTo(regla.getProgresoMinimo()) >= 0;
            condiciones.add(new CondicionAvanceRespuesta("PROGRESO", "Progreso del curso",
                    porcentaje + " %", "mínimo " + numero(regla.getProgresoMinimo()) + " %", cumplida));
            if (!cumplida) {
                faltantes.add("completar al menos " + numero(regla.getProgresoMinimo())
                        + " % de las lecciones obligatorias (llevas " + porcentaje + " %)");
            }
        }

        if (regla.isRequiereExamenes()) {
            List<Examen> calificados = ruta.examenesCalificados();
            long aprobados = calificados.stream().filter(e -> ruta.examenesAprobados().contains(e.getId())).count();
            long pendientes = calificados.size() - aprobados;
            boolean examenesCumplidos = pendientes == 0;
            condiciones.add(new CondicionAvanceRespuesta("EXAMENES", "Exámenes calificados",
                    aprobados + " de " + calificados.size() + " aprobados",
                    "todos aprobados", examenesCumplidos));
            if (!examenesCumplidos) {
                faltantes.add("aprobar " + pendientes + (pendientes == 1 ? " examen más" : " exámenes más"));
            }

            List<BigDecimal> mejores = calificados.stream()
                    .map(e -> ruta.mejoresNotas().get(e.getId())).filter(n -> n != null).toList();
            BigDecimal promedio = mejores.isEmpty() ? null
                    : mejores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(mejores.size()), 2, RoundingMode.HALF_UP);
            boolean notaCumplida = promedio != null && promedio.compareTo(regla.getNotaMinima()) >= 0;
            condiciones.add(new CondicionAvanceRespuesta("NOTA", "Nota promedio",
                    promedio == null ? "Sin notas aún" : numero(promedio),
                    "mínimo " + numero(regla.getNotaMinima()), notaCumplida));
            if (!notaCumplida && promedio != null) {
                faltantes.add("subir tu nota promedio a " + numero(regla.getNotaMinima()) + " (llevas " + numero(promedio) + ")");
            }
        }

        if (regla.isRequiereAsistencia()) {
            Integer asistencia = sesionAlumno.porcentajeAsistencia(matricula.getId());
            // "No aplica" (cero sesiones elegibles) nunca equivale a cumplido (HU-026).
            boolean cumplida = asistencia != null && BigDecimal.valueOf(asistencia).compareTo(regla.getAsistenciaMinima()) >= 0;
            condiciones.add(new CondicionAvanceRespuesta("ASISTENCIA", "Asistencia a sesiones en vivo",
                    asistencia == null ? "No aplica" : asistencia + " %",
                    "mínimo " + numero(regla.getAsistenciaMinima()) + " %", cumplida));
            if (!cumplida) {
                faltantes.add(asistencia == null
                        ? "asistir a las sesiones en vivo (aún no hay sesiones que cuenten)"
                        : "llegar a " + numero(regla.getAsistenciaMinima()) + " % de asistencia (llevas " + asistencia + " %)");
            }
        }

        boolean cumpleTodo = faltantes.isEmpty();
        String frase = cumpleTodo
                ? "Cumples todos los requisitos activos para tu certificado."
                : "Te falta: " + String.join("; ", faltantes) + ".";
        return new AvanceCertificadoRespuesta(condiciones, cumpleTodo, frase);
    }

    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
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
