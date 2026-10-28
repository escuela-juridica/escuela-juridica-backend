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
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.AsistenciaRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ProgresoMaterialRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-024 — Completar lecciones y registrar progreso: lado de escritura que complementa a {@code
 * AulaServicio} (HU-022, solo lectura). Registra el avance de reproducción de video por material y
 * decide cuándo una lección queda {@code completada}, ya sea automáticamente (umbral de video),
 * manualmente (check/"Completar lección") o por asistencia a una sesión en vivo (hook invocado por
 * {@code SesionAlumnoServicio} y {@code AdministrarSesionesServicio}).
 */
@Service
@RequiredArgsConstructor
public class ProgresoServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MatriculaRepositorio matriculas;
    private final LeccionRepositorio lecciones;
    private final MaterialLeccionRepositorio materiales;
    private final ProgresoLeccionRepositorio progresoLecciones;
    private final ProgresoMaterialRepositorio progresoMateriales;
    private final ReglaCursoRepositorio reglasCurso;
    private final AsistenciaRepositorio asistencias;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional
    public void registrarProgresoVideo(Long materialLeccionId, int segundosReproducidos, int ultimaPosicionSegundos) {
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

        Recurso recurso = material.getRecurso();
        if (!recurso.isDuracionDetectada() || !"VIDEO".equals(recurso.getTipo())) {
            throw new BusinessValidationException("Este material no permite registrar progreso de video.");
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
        // El porcentaje guardado nunca retrocede: un alumno que rebobina no pierde progreso ya alcanzado.
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
                .filter(m -> "VIDEO".equals(m.getRecurso().getTipo()) && m.getRecurso().isDuracionDetectada())
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

        List<MaterialLeccion> materialesActivos = materiales.findByLeccion_IdOrderByOrdenAsc(leccionId).stream()
                .filter(MaterialLeccion::isActivo)
                .toList();
        boolean tieneVideoDetectable = materialesActivos.stream()
                .anyMatch(m -> "VIDEO".equals(m.getRecurso().getTipo()) && m.getRecurso().isDuracionDetectada());
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
                // El alumno estuvo ausente (o la sesión ni terminó): depende de la grabación. Sin
                // ningún material activo todavía (ni siquiera uno de fuente no detectable), la
                // grabación aún no está disponible y la acción manual no procede.
                if (materialesActivos.isEmpty()) {
                    throw new BusinessValidationException("La grabación todavía no está disponible.");
                }
                // Hay al menos un material activo; como ya se descartó el video detectable arriba,
                // es necesariamente una fuente no detectable (p. ej. un enlace externo): se permite
                // la finalización manual.
            }
        }

        marcarCompletadaSiNoLoEstaba(matricula, leccion, "MANUAL");
    }

    /** Invocado por el propio backend (job de HU-026, corrección de HU-027), nunca directamente
     * por un alumno: no valida rol, igual que {@code SesionAlumnoServicio#porcentajeAsistencia}. */
    public void completarPorAsistencia(Matricula matricula, Leccion leccion, String estadoAsistencia) {
        if ("PRESENTE".equals(estadoAsistencia) || "JUSTIFICADA".equals(estadoAsistencia)) {
            marcarCompletadaSiNoLoEstaba(matricula, leccion, "ASISTENCIA");
        }
    }

    /** Ningún check completado puede desmarcarse: si ya estaba completada, no se toca nada (ni
     * forma de completado ni fecha de completado previas). */
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
    }

    /** Registra que el alumno "reabrió" la lección sin alcanzar aún el umbral de completado. Por
     * la regla "ningún check completado puede desmarcarse", si la lección ya estaba completada
     * (por otra vía) esto nunca la revierte a no completada. */
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

    /** Replica MatriculaServicio#respuesta(...) / AulaServicio#verificarAcceso(...): es la
     * definición oficial de acceso efectivo en todo el proyecto y no debe divergir. Duplicado a
     * propósito en cada servicio, no se extrae a un helper compartido. */
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
