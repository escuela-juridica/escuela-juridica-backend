package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.MatriculaAlcanceFila;
import pe.edu.utp.escuela.app.dto.OpcionAsistenciaRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteAsistenciaFila;
import pe.edu.utp.escuela.app.dto.ReporteAsistenciaRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.AsistenciaRepositorio;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-045 — Reporte de asistencia (administrador). Solo cursos EN_VIVO e HIBRIDO. La tabla y la
 * exportación a Excel salen de la misma consulta y el mismo cálculo por alumno: el porcentaje
 * acumulado es el de {@link SesionAlumnoServicio#porcentajeAsistencia(Long)} (sesiones asistidas
 * entre sesiones elegibles, excluyendo canceladas y las anteriores a una matrícula tardía), el
 * mismo que ve el alumno en su avance, así que nunca divergen. Las sesiones canceladas no
 * aparecen en el reporte porque no entran al denominador.
 */
@Service
@RequiredArgsConstructor
public class ReporteAsistenciaServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<String> ASISTENCIAS = Set.of("PRESENTE", "AUSENTE", "JUSTIFICADA", "PENDIENTE");
    private static final Set<String> ORIGENES = Set.of("AUTOMATICA", "CORRECCION_ADMINISTRATIVA");
    private static final Set<String> CUMPLIMIENTOS = Set.of("CUMPLE", "NO_CUMPLE");

    private final AsistenciaRepositorio asistencias;
    private final CursoRepositorio cursos;
    private final LeccionRepositorio lecciones;
    private final ReglaCursoRepositorio reglasCurso;
    private final SesionAlumnoServicio sesionAlumno;
    private final CurrentUserService actual;

    /** Filtros del reporte (todos opcionales); las cadenas vacías significan "sin filtro". */
    public record Filtro(
            String texto, Long cursoId, Long sesionId, String modalidad,
            LocalDate desde, LocalDate hasta, String asistencia, String origen, String cumplimiento) {
    }

    @Transactional(readOnly = true)
    public Page<ReporteAsistenciaRespuesta> paginado(Filtro filtro, Pageable pageable) {
        Consulta consulta = preparar(filtro);
        if (consulta.sinResultados) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Page<ReporteAsistenciaFila> pagina = asistencias.buscarReporte(
                consulta.texto, filtro.cursoId(), filtro.sesionId(), consulta.modalidad,
                consulta.desde, consulta.hasta, consulta.asistencia, consulta.origen,
                consulta.restringir, consulta.matriculaIds, pageable);
        return pagina.map(enriquecedor(pagina.getContent()));
    }

    @Transactional(readOnly = true)
    public List<ReporteAsistenciaRespuesta> completo(Filtro filtro) {
        Consulta consulta = preparar(filtro);
        if (consulta.sinResultados) {
            return List.of();
        }
        List<ReporteAsistenciaFila> filas = asistencias.exportarReporte(
                consulta.texto, filtro.cursoId(), filtro.sesionId(), consulta.modalidad,
                consulta.desde, consulta.hasta, consulta.asistencia, consulta.origen,
                consulta.restringir, consulta.matriculaIds);
        return filas.stream().map(enriquecedor(filas)).toList();
    }

    /** Cursos que ofrece el filtro: nunca un curso VIRTUAL. */
    @Transactional(readOnly = true)
    public List<OpcionAsistenciaRespuesta> cursosDisponibles() {
        exigirAdministrador();
        return cursos.findAll().stream()
                .filter(c -> "EN_VIVO".equals(c.getModalidad()) || "HIBRIDO".equals(c.getModalidad()))
                .sorted((a, b) -> a.getTitulo().compareToIgnoreCase(b.getTitulo()))
                .map(c -> new OpcionAsistenciaRespuesta(c.getId(), c.getTitulo(), c.getModalidad(), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpcionAsistenciaRespuesta> sesionesDeCurso(Long cursoId) {
        exigirAdministrador();
        exigirCursoEnVivo(cursoId);
        return lecciones.buscarSesionesEnVivoDeCurso(cursoId).stream()
                .filter(l -> !"CANCELADA".equals(l.getEstado()) && l.getFechaHoraInicio() != null)
                .sorted((a, b) -> a.getFechaHoraInicio().compareTo(b.getFechaHoraInicio()))
                .map(l -> new OpcionAsistenciaRespuesta(l.getId(), l.getTitulo(),
                        l.getModulo().getTitulo(), l.getFechaHoraInicio()))
                .toList();
    }

    // ------------------------------------------------------------------ internos --

    private record Consulta(
            boolean sinResultados, String texto, String modalidad, String asistencia, String origen,
            Instant desde, Instant hasta, boolean restringir, List<Long> matriculaIds) {
    }

    private Consulta preparar(Filtro filtro) {
        exigirAdministrador();
        String modalidad = mayusculas(filtro.modalidad());
        if ("VIRTUAL".equals(modalidad)) {
            throw new BusinessValidationException("La asistencia no aplica a cursos virtuales.");
        }
        if (!modalidad.isEmpty() && !"EN_VIVO".equals(modalidad) && !"HIBRIDO".equals(modalidad)) {
            throw new BusinessValidationException("Modalidad no válida para el reporte de asistencia.");
        }
        if (filtro.cursoId() != null) {
            exigirCursoEnVivo(filtro.cursoId());
        }
        String asistencia = mayusculas(filtro.asistencia());
        if (!asistencia.isEmpty() && !ASISTENCIAS.contains(asistencia)) {
            throw new BusinessValidationException("Estado de asistencia no válido.");
        }
        String origen = mayusculas(filtro.origen());
        if (!origen.isEmpty() && !ORIGENES.contains(origen)) {
            throw new BusinessValidationException("Origen de asistencia no válido.");
        }
        String cumplimiento = mayusculas(filtro.cumplimiento());
        if (!cumplimiento.isEmpty() && !CUMPLIMIENTOS.contains(cumplimiento)) {
            throw new BusinessValidationException("Filtro de cumplimiento no válido.");
        }
        if (filtro.desde() != null && filtro.hasta() != null && filtro.hasta().isBefore(filtro.desde())) {
            throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial.");
        }

        String texto = filtro.texto() == null ? "" : filtro.texto().strip().toLowerCase();
        Instant desde = filtro.desde() == null
                ? LocalDate.of(1, 1, 1).atStartOfDay(LIMA).toInstant()
                : filtro.desde().atStartOfDay(LIMA).toInstant();
        Instant hasta = filtro.hasta() == null
                ? LocalDate.of(9999, 12, 31).plusDays(1).atStartOfDay(LIMA).toInstant()
                : filtro.hasta().plusDays(1).atStartOfDay(LIMA).toInstant();

        if (cumplimiento.isEmpty()) {
            return new Consulta(false, texto, modalidad, asistencia, origen, desde, hasta, false, List.of(-1L));
        }
        // El cumplimiento depende del porcentaje de cada alumno, que no es una columna: se calcula
        // para las matrículas en alcance y la consulta se restringe a las que lo cumplen o no.
        List<MatriculaAlcanceFila> enAlcance = asistencias.matriculasEnAlcance(texto, filtro.cursoId(), modalidad);
        Map<Long, ReglaCurso> reglas = reglasPorCurso(
                enAlcance.stream().map(MatriculaAlcanceFila::cursoId).distinct().toList());
        List<Long> elegidas = enAlcance.stream()
                .filter(m -> cumplimiento.equals(cumplimiento(reglas.get(m.cursoId()),
                        sesionAlumno.porcentajeAsistencia(m.matriculaId()))))
                .map(MatriculaAlcanceFila::matriculaId)
                .toList();
        if (elegidas.isEmpty()) {
            return new Consulta(true, texto, modalidad, asistencia, origen, desde, hasta, true, List.of(-1L));
        }
        return new Consulta(false, texto, modalidad, asistencia, origen, desde, hasta, true, elegidas);
    }

    private java.util.function.Function<ReporteAsistenciaFila, ReporteAsistenciaRespuesta> enriquecedor(
            List<ReporteAsistenciaFila> filas) {
        Map<Long, Integer> porcentajes = new HashMap<>();
        Map<Long, Boolean> calculados = new HashMap<>();
        for (ReporteAsistenciaFila fila : filas) {
            if (calculados.putIfAbsent(fila.matriculaId(), Boolean.TRUE) == null) {
                porcentajes.put(fila.matriculaId(), sesionAlumno.porcentajeAsistencia(fila.matriculaId()));
            }
        }
        Map<Long, ReglaCurso> reglas = reglasPorCurso(
                filas.stream().map(ReporteAsistenciaFila::cursoId).distinct().toList());
        Map<Long, Long> cursoPorMatricula = filas.stream().collect(Collectors.toMap(
                ReporteAsistenciaFila::matriculaId, ReporteAsistenciaFila::cursoId, (a, b) -> a));
        return fila -> {
            Integer porcentaje = porcentajes.get(fila.matriculaId());
            ReglaCurso regla = reglas.get(cursoPorMatricula.get(fila.matriculaId()));
            return new ReporteAsistenciaRespuesta(
                    fila.matriculaId(), fila.cursoId(), fila.curso(), fila.modalidad(),
                    fila.leccionId(), fila.sesion(), fila.fechaSesion(), fila.alumno(), fila.correo(),
                    fila.asistencia(), fila.origen(), fila.motivoCorreccion(), porcentaje,
                    cumplimiento(regla, porcentaje));
        };
    }

    private Map<Long, ReglaCurso> reglasPorCurso(List<Long> cursoIds) {
        if (cursoIds.isEmpty()) {
            return Map.of();
        }
        return reglasCurso.findByCurso_IdIn(cursoIds).stream()
                .collect(Collectors.toMap(r -> r.getCurso().getId(), r -> r, (a, b) -> a));
    }

    /** CUMPLE/NO_CUMPLE solo si el curso exige asistencia mínima; "No aplica" (sin sesiones
     * elegibles) nunca se considera cumplido. {@code null} si no es condición del curso. */
    private static String cumplimiento(ReglaCurso regla, Integer porcentaje) {
        if (regla == null || !regla.isRequiereAsistencia()) {
            return null;
        }
        boolean cumple = porcentaje != null
                && BigDecimal.valueOf(porcentaje).compareTo(regla.getAsistenciaMinima()) >= 0;
        return cumple ? "CUMPLE" : "NO_CUMPLE";
    }

    private void exigirCursoEnVivo(Long cursoId) {
        Curso curso = cursos.findById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso no existe."));
        if (!"EN_VIVO".equals(curso.getModalidad()) && !"HIBRIDO".equals(curso.getModalidad())) {
            throw new BusinessValidationException("La asistencia no aplica a cursos virtuales.");
        }
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
