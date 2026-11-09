package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.entity.Asistencia;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.entity.ProgresoMaterial;
import pe.edu.utp.escuela.app.entity.Recurso;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.event.ReevaluarCertificacionEvent;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.AsistenciaRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepository;
import pe.edu.utp.escuela.app.repository.ProgresoMaterialRepository;
import pe.edu.utp.escuela.app.repository.RecursoRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

@Service
@RequiredArgsConstructor
public class ProgresoService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MatriculaRepository matriculas;
    private final LeccionRepository lecciones;
    private final MaterialLeccionRepository materiales;
    private final ProgresoLeccionRepository progresoLecciones;
    private final ProgresoMaterialRepository progresoMateriales;
    private final ReglaCursoRepository reglasCurso;
    private final RecursoRepository recursos;
    private final AsistenciaRepository asistencias;
    private final RutaAcademicaService rutaAcademica;
    private final ApplicationEventPublisher eventos;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional
    public void registrarProgresoVideo(
            Long materialLeccionId, int segundosReproducidos, int ultimaPosicionSegundos,
            Integer duracionSegundosCliente) {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
        MaterialLeccion material = materiales.findById(materialLeccionId)
                .filter(MaterialLeccion::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("El material no existe."));
        Leccion leccion = material.getLeccion();
        Curso curso = leccion.getModulo().getCurso();

        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), curso.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));
        verificarAcceso(matricula);
        exigirSecuenciaLibre(matricula, leccion);

        Recurso recurso = material.getRecurso();
        if (!esVideoDetectable(material)) {
            throw new BusinessValidationException("Este material no permite registrar progreso de video.");
        }
        if (recurso.getDuracionSegundos() == null && duracionSegundosCliente != null) {
            recurso.setDuracionSegundos(duracionSegundosCliente);
            recurso.setDuracionDetectada(true);
            recursos.save(recurso);
        }
        if (!recurso.isDuracionDetectada()) {
            return;
        }

        int duracionSegundos = recurso.getDuracionSegundos() != null ? recurso.getDuracionSegundos() : 0;
        double nuevoPorcentaje = duracionSegundos > 0
                ? Math.min(100.0, segundosReproducidos * 100.0 / duracionSegundos)
                : 0.0;
        BigDecimal nuevoPorcentajeBd = BigDecimal.valueOf(nuevoPorcentaje).setScale(2, RoundingMode.HALF_UP);

        Instant ahora = clock.instant();
        ProgresoMaterial progreso = progresoMateriales
                .findByMatricula_IdAndMaterialLeccion_Id(matricula.getId(), materialLeccionId)
                .orElseGet(() -> {
                    ProgresoMaterial nuevo = new ProgresoMaterial();
                    nuevo.setMatricula(matricula);
                    nuevo.setMaterialLeccion(material);
                    nuevo.setPorcentajeVideo(BigDecimal.ZERO);
                    return nuevo;
                });
        progreso.setPorcentajeVideo(progreso.getPorcentajeVideo().max(nuevoPorcentajeBd));
        progreso.setSegundosReproducidos(segundosReproducidos);
        progreso.setUltimaPosicionSegundos(ultimaPosicionSegundos);
        progreso.setActualizadoEn(ahora);
        progresoMateriales.save(progreso);

        BigDecimal umbral = reglasCurso.findByCurso_Id(curso.getId())
                .map(ReglaCurso::getUmbralVideo).orElse(BigDecimal.valueOf(50));

        List<MaterialLeccion> materialesVideoLeccion = materiales.findByLeccion_IdOrderByOrdenAsc(leccion.getId())
                .stream()
                .filter(MaterialLeccion::isActivo)
                .filter(this::esVideoDetectable)
                .toList();

        boolean umbralAlcanzado = false;
        if (!materialesVideoLeccion.isEmpty()) {
            List<Long> idsVideo = materialesVideoLeccion.stream().map(MaterialLeccion::getId).toList();
            Map<Long, BigDecimal> porcentajesPorMaterial = progresoMateriales
                    .findByMatricula_IdAndMaterialLeccion_IdIn(matricula.getId(), idsVideo).stream()
                    .collect(Collectors.toMap(
                            pm -> pm.getMaterialLeccion().getId(), ProgresoMaterial::getPorcentajeVideo));
            umbralAlcanzado = idsVideo.stream().allMatch(
                    id -> porcentajesPorMaterial.getOrDefault(id, BigDecimal.ZERO).compareTo(umbral) >= 0);
        }

        if (umbralAlcanzado) {
            marcarCompletadaSiNoLoEstaba(matricula, leccion, "AUTOMATICO");
        } else {
            actualizarVisitaSinCompletar(matricula, leccion);
        }
    }

    @Transactional
    public void completarManual(Long leccionId) {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
        Leccion leccion = lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("La lección no existe."));
        Curso curso = leccion.getModulo().getCurso();

        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), curso.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));
        verificarAcceso(matricula);
        exigirSecuenciaLibre(matricula, leccion);

        List<MaterialLeccion> materialesActivos = materiales.findByLeccion_IdOrderByOrdenAsc(leccionId).stream()
                .filter(MaterialLeccion::isActivo)
                .toList();
        boolean tieneVideoDetectable = materialesActivos.stream().anyMatch(this::esVideoDetectable);
        if (tieneVideoDetectable) {
            throw new BusinessValidationException("Esta lección se completa automáticamente al ver el video.");
        }

        if ("EN_VIVO".equals(leccion.getTipo())) {
            Optional<Asistencia> asistencia =
                    asistencias.findByMatricula_IdAndLeccion_Id(matricula.getId(), leccionId);
            boolean presenteOJustificada = asistencia.isPresent()
                    && ("PRESENTE".equals(asistencia.get().getEstado())
                            || "JUSTIFICADA".equals(asistencia.get().getEstado()));
            if (!presenteOJustificada) {
                if (materialesActivos.isEmpty()) {
                    throw new BusinessValidationException("La grabación todavía no está disponible.");
                }
            }
        }

        marcarCompletadaSiNoLoEstaba(matricula, leccion, "MANUAL");
    }

    private boolean esVideoDetectable(MaterialLeccion material) {
        Recurso recurso = material.getRecurso();
        return "VIDEO".equals(recurso.getTipo()) && "SUBIDO".equals(recurso.getOrigen());
    }

    public void completarPorAsistencia(Matricula matricula, Leccion leccion, String estadoAsistencia) {
        if ("PRESENTE".equals(estadoAsistencia) || "JUSTIFICADA".equals(estadoAsistencia)) {
            marcarCompletadaSiNoLoEstaba(matricula, leccion, "ASISTENCIA");
        }
    }

    private void marcarCompletadaSiNoLoEstaba(Matricula matricula, Leccion leccion, String formaCompletado) {
        ProgresoLeccion progreso = progresoLecciones
                .findByMatricula_IdAndLeccion_Id(matricula.getId(), leccion.getId())
                .orElseGet(ProgresoLeccion::new);
        if (progreso.isCompletada()) {
            return;
        }
        if (progreso.getId() == null) {
            progreso.setMatricula(matricula);
            progreso.setLeccion(leccion);
        }
        Instant ahora = clock.instant();
        progreso.setCompletada(true);
        progreso.setFormaCompletado(formaCompletado);
        progreso.setCompletadaEn(ahora);
        progreso.setUltimaVisitaEn(ahora);
        progresoLecciones.save(progreso);
        eventos.publishEvent(new ReevaluarCertificacionEvent(matricula.getId()));
    }

    private void actualizarVisitaSinCompletar(Matricula matricula, Leccion leccion) {
        ProgresoLeccion progreso = progresoLecciones
                .findByMatricula_IdAndLeccion_Id(matricula.getId(), leccion.getId())
                .orElseGet(ProgresoLeccion::new);
        if (progreso.getId() == null) {
            progreso.setMatricula(matricula);
            progreso.setLeccion(leccion);
            progreso.setCompletada(false);
        }
        progreso.setUltimaVisitaEn(clock.instant());
        progresoLecciones.save(progreso);
    }

    private void exigirSecuenciaLibre(Matricula matricula, Leccion leccion) {
        String motivo = rutaAcademica.calcular(matricula).motivoBloqueo(leccion.getId());
        if (motivo != null) {
            throw new BusinessValidationException(motivo);
        }
    }

    private void verificarAcceso(Matricula m) {
        if ("CANCELADO".equals(m.getCurso().getEstadoCurso().getCodigo())) {
            throw new BusinessValidationException(
                    "El curso fue cancelado: puedes consultar su contenido, pero ya no se registra progreso.");
        }
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
