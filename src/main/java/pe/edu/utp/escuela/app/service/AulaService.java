package pe.edu.utp.escuela.app.service;

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
import pe.edu.utp.escuela.app.dto.AulaResponse;
import pe.edu.utp.escuela.app.dto.DuracionLeccionRow;
import pe.edu.utp.escuela.app.dto.LeccionAulaResponse;
import pe.edu.utp.escuela.app.dto.ModuloAulaResponse;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/** HU-022 — Ingresar y continuar un curso: resuelve el acceso efectivo de la matrícula del
 * alumno actual a un curso y construye el temario (módulos → lecciones) con el estado de cada
 * lección y el punto donde "Continuar" debe reabrir el aula. HU-025 — el orden y los bloqueos
 * vienen de {@link RutaAcademicaService} y el aula suma los exámenes de cada módulo y el bloque
 * "Tu avance hacia el certificado".
 *
 * <p>El cálculo de "acceso efectivo" replica exactamente, a propósito, el de {@code
 * MatriculaService#respuesta(Matricula, String)}: es la definición oficial de acceso efectivo en
 * todo el proyecto y no debe divergir. */
@Service
@RequiredArgsConstructor
public class AulaService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MatriculaRepository matriculas;
    private final MaterialLeccionRepository materiales;
    private final RutaAcademicaService rutaAcademica;
    private final AvanceAcademicoService avanceAcademico;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AulaResponse temario(Long cursoId) {
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

        // Duración de la lección = la del video más largo entre sus materiales activos (si la hay).
        Map<Long, Integer> duraciones = idsLecciones.isEmpty() ? Map.of()
                : materiales.buscarDuraciones(idsLecciones).stream()
                        .filter(f -> f.getDuracionSegundos() != null)
                        .collect(Collectors.toMap(DuracionLeccionRow::getLeccionId,
                                DuracionLeccionRow::getDuracionSegundos, (a, b) -> a));

        Instant ahora = clock.instant();
        Map<Long, LeccionAulaResponse> respuestaPorLeccion = new java.util.HashMap<>();
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

            respuestaPorLeccion.put(leccion.getId(), new LeccionAulaResponse(
                    leccion.getId(), leccion.getTitulo(), leccion.getTipo(), leccion.getOrden(),
                    leccion.isEsObligatoria(), estado, motivoBloqueo,
                    leccion.getFechaHoraInicio(), leccion.getFechaHoraFin(), estadoSesion, grabacionDisponible,
                    leccion.getDescripcion(), duraciones.get(leccion.getId())));
        }

        List<ModuloAulaResponse> modulosRespuesta = new ArrayList<>();
        for (Modulo modulo : ruta.modulos()) {
            List<LeccionAulaResponse> delModulo = ruta.leccionesPorModulo().get(modulo.getId()).stream()
                    .map(l -> respuestaPorLeccion.get(l.getId())).toList();
            modulosRespuesta.add(new ModuloAulaResponse(modulo.getId(), modulo.getTitulo(), modulo.getOrden(),
                    delModulo, ruta.examenesPorModulo().getOrDefault(modulo.getId(), List.of())));
        }

        List<Leccion> obligatorias = globalOrden.stream()
                .filter(l -> l.isEsObligatoria() && !"CANCELADA".equals(l.getEstado())).toList();
        List<Leccion> baseParaPorcentaje = obligatorias.isEmpty() ? globalOrden : obligatorias;
        AvanceAcademico avance = avanceAcademico.calcular(matricula, ruta);

        Long ultimaLeccionIdValida = ultimaLeccionValida(baseParaPorcentaje, respuestaPorLeccion);

        return new AulaResponse(matricula.getId(), matricula.getCurso().getId(), matricula.getCurso().getTitulo(),
                matricula.getCurso().getModalidad(), avance.porcentaje(), modulosRespuesta, ultimaLeccionIdValida,
                avance.comoRespuesta(), ruta.examenesFinales());
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
    private Long ultimaLeccionValida(List<Leccion> base, Map<Long, LeccionAulaResponse> respuestaPorLeccion) {
        Leccion ultimaCompletada = null;
        for (Leccion leccion : base) {
            LeccionAulaResponse r = respuestaPorLeccion.get(leccion.getId());
            if ("DISPONIBLE".equals(r.estado())) {
                return leccion.getId();
            }
            if ("COMPLETADA".equals(r.estado())) {
                ultimaCompletada = leccion;
            }
        }
        return ultimaCompletada != null ? ultimaCompletada.getId() : null;
    }

    /** Replica MatriculaService#respuesta(...) para el cálculo de acceso efectivo, y añade el
     * caso de HU-022: un curso CANCELADO conserva el acceso de lectura de quien ya tenía
     * matrícula (no cancelada individualmente) antes de la cancelación. Como MatriculaService
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
