package pe.edu.utp.escuela.app.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CondicionAvanceResponse;
import pe.edu.utp.escuela.app.dto.OpcionAsistenciaResponse;
import pe.edu.utp.escuela.app.dto.PendienteRevisionRow;
import pe.edu.utp.escuela.app.dto.ReporteAcademicoResponse;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.RespuestaIntentoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;

/**
 * HU-043 — Reporte de seguimiento académico (administrador): una fila por matrícula con progreso,
 * exámenes, nota final, asistencia, respuestas pendientes de revisión, condiciones pendientes,
 * finalización y certificado. La pantalla y la exportación a Excel salen de la misma consulta y del
 * mismo cálculo ({@link AvanceAcademicoService}, el que usa el aula del alumno): las condiciones
 * activas, sus valores reales y sus metas coinciden en todas partes y nunca se inventa una
 * asistencia o una nota que el curso no exige.
 *
 * <p>El rango de fechas se aplica a {@code fecha_matricula}. Los filtros de situación,
 * cumplimiento y certificado dependen del cálculo por alumno (no son columnas), así que solo
 * entonces se evalúa todo el universo filtrado antes de paginar; sin ellos se pagina en la consulta
 * y se calcula únicamente la página visible.
 */
@Service
@RequiredArgsConstructor
public class ReporteAcademicoService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<String> MODALIDADES = Set.of("VIRTUAL", "EN_VIVO", "HIBRIDO");
    private static final Set<String> SITUACIONES = Set.of("EN_PROGRESO", "EN_REVISION", "FINALIZADO", "CANCELADA");
    private static final Set<String> CUMPLIMIENTOS = Set.of("CUMPLE", "NO_CUMPLE");
    private static final Set<String> CERTIFICADOS = Set.of("SIN_EMITIR", "EMITIDO", "ANULADO");

    private final MatriculaRepository matriculas;
    private final CursoRepository cursos;
    private final CertificadoRepository certificados;
    private final RespuestaIntentoRepository respuestas;
    private final AvanceAcademicoService avanceAcademico;
    private final CurrentUserService actual;

    /** Filtros del reporte (todos opcionales); las cadenas vacías significan "sin filtro". */
    public record Filtro(
            String texto, Long cursoId, String modalidad, LocalDate desde, LocalDate hasta,
            String situacion, String cumplimiento, String certificado) {
    }

    @Transactional(readOnly = true)
    public Page<ReporteAcademicoResponse> paginado(Filtro filtro, Pageable pageable) {
        Consulta consulta = preparar(filtro);
        if (!consulta.filtraPorCalculo) {
            Page<Matricula> pagina = matriculas.buscarReporteAcademico(consulta.texto, filtro.cursoId(),
                    consulta.modalidad, consulta.desde, consulta.hasta, pageable);
            return new PageImpl<>(filas(pagina.getContent()), pageable, pagina.getTotalElements());
        }
        List<ReporteAcademicoResponse> todas = filas(matriculas.exportarReporteAcademico(consulta.texto,
                filtro.cursoId(), consulta.modalidad, consulta.desde, consulta.hasta)).stream()
                .filter(consulta.coincide).toList();
        int inicio = (int) Math.min(pageable.getOffset(), todas.size());
        int fin = Math.min(inicio + pageable.getPageSize(), todas.size());
        return new PageImpl<>(todas.subList(inicio, fin), pageable, todas.size());
    }

    @Transactional(readOnly = true)
    public List<ReporteAcademicoResponse> completo(Filtro filtro) {
        Consulta consulta = preparar(filtro);
        List<ReporteAcademicoResponse> todas = filas(matriculas.exportarReporteAcademico(consulta.texto,
                filtro.cursoId(), consulta.modalidad, consulta.desde, consulta.hasta));
        return consulta.filtraPorCalculo ? todas.stream().filter(consulta.coincide).toList() : todas;
    }

    @Transactional(readOnly = true)
    public List<OpcionAsistenciaResponse> cursosDisponibles() {
        exigirAdministrador();
        return cursos.findAll().stream()
                .sorted((a, b) -> a.getTitulo().compareToIgnoreCase(b.getTitulo()))
                .map(c -> new OpcionAsistenciaResponse(c.getId(), c.getTitulo(), c.getModalidad(), null))
                .toList();
    }

    // ------------------------------------------------------------------ internos --

    private record Consulta(String texto, String modalidad, Instant desde, Instant hasta,
            boolean filtraPorCalculo, Predicate<ReporteAcademicoResponse> coincide) {
    }

    private Consulta preparar(Filtro filtro) {
        exigirAdministrador();
        String modalidad = mayusculas(filtro.modalidad());
        if (!modalidad.isEmpty() && !MODALIDADES.contains(modalidad)) {
            throw new BusinessValidationException("Modalidad no válida.");
        }
        String situacion = mayusculas(filtro.situacion());
        if (!situacion.isEmpty() && !SITUACIONES.contains(situacion)) {
            throw new BusinessValidationException("Situación académica no válida.");
        }
        String cumplimiento = mayusculas(filtro.cumplimiento());
        if (!cumplimiento.isEmpty() && !CUMPLIMIENTOS.contains(cumplimiento)) {
            throw new BusinessValidationException("Filtro de cumplimiento no válido.");
        }
        String certificado = mayusculas(filtro.certificado());
        if (!certificado.isEmpty() && !CERTIFICADOS.contains(certificado)) {
            throw new BusinessValidationException("Estado de certificado no válido.");
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

        boolean porCalculo = !situacion.isEmpty() || !cumplimiento.isEmpty() || !certificado.isEmpty();
        Predicate<ReporteAcademicoResponse> coincide = fila ->
                (situacion.isEmpty() || situacion.equals(fila.situacion()))
                        && (cumplimiento.isEmpty() || cumplimiento.equals(fila.cumplimiento()))
                        && (certificado.isEmpty() || certificado.equals(fila.certificado()));
        return new Consulta(texto, modalidad, desde, hasta, porCalculo, coincide);
    }

    private List<ReporteAcademicoResponse> filas(List<Matricula> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lista.stream().map(Matricula::getId).toList();
        Map<Long, PendienteRevisionRow> pendientes = respuestas.pendientesPorMatricula(ids).stream()
                .collect(Collectors.toMap(PendienteRevisionRow::matriculaId, f -> f, (a, b) -> a));
        Map<Long, Certificado> certificadoPorMatricula = certificados.findByLogro_Matricula_IdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getLogro().getMatricula().getId(), c -> c, (a, b) -> a));
        return lista.stream().map(m -> fila(m, avanceAcademico.calcular(m), pendientes.get(m.getId()),
                certificadoPorMatricula.get(m.getId()))).toList();
    }

    private ReporteAcademicoResponse fila(Matricula m, AvanceAcademico avance, PendienteRevisionRow pendiente,
            Certificado certificado) {
        boolean exigeExamenes = avance.ruta().regla().isRequiereExamenes();
        List<String> condicionesPendientes = avance.condiciones().stream()
                .filter(c -> !c.cumplida())
                .map(ReporteAcademicoService::describir).toList();
        if (avance.bloqueadoPorRevision() && condicionesPendientes.isEmpty()) {
            condicionesPendientes = List.of("Calificación de un examen con respuesta abierta");
        }

        String situacion = "CANCELADA".equals(m.getEstado()) ? "CANCELADA"
                : avance.bloqueadoPorRevision() ? "EN_REVISION"
                        : avance.cumpleTodo() ? "FINALIZADO" : "EN_PROGRESO";
        String estadoCertificado = certificado == null ? "SIN_EMITIR"
                : "ANULADO".equals(certificado.getEstado()) ? "ANULADO" : "EMITIDO";

        return new ReporteAcademicoResponse(m.getId(), m.getCurso().getId(), m.getCurso().getTitulo(),
                m.getCurso().getModalidad(), m.getUsuario().getPersona().nombreCompleto(), m.getUsuario().getCorreo(),
                m.getFechaMatricula(), m.getEstado(), avance.porcentaje(), exigeExamenes, avance.examenesRequeridos(),
                avance.examenesAprobados(), avance.notaFinal(), avance.asistencia(), avance.asistenciaExigida(),
                pendiente == null ? 0 : pendiente.cantidad(), pendiente == null ? null : pendiente.fechaLimite(),
                avance.condiciones(), condicionesPendientes, situacion, avance.cumpleTodo() ? "CUMPLE" : "NO_CUMPLE",
                m.getFechaFinalizacion(), estadoCertificado, certificado == null ? null : certificado.getCodigo());
    }

    private static String describir(CondicionAvanceResponse c) {
        return c.titulo() + ": " + c.valorActual() + " (" + c.meta() + ")";
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
