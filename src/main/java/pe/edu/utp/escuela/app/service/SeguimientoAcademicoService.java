package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CorregirAsistenciaRequest;
import pe.edu.utp.escuela.app.dto.IntentoResumenResponse;
import pe.edu.utp.escuela.app.dto.OtorgarIntentoRequest;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoResponse;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoResponse.CertificadoSeguimiento;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoResponse.ExamenSeguimiento;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoResponse.ExcepcionResponse;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoResponse.LeccionSeguimiento;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoResponse.ModuloSeguimiento;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoResponse.SesionSeguimiento;
import pe.edu.utp.escuela.app.entity.Asistencia;
import pe.edu.utp.escuela.app.entity.ExcepcionAcademica;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.AsistenciaRepository;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.EstadoExamen;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/**
 * HU-031 — Atender excepciones académicas. Administración consulta el desempeño completo de un
 * alumno en un curso (solo lectura) y puede corregir una asistencia o conceder UN intento
 * adicional, siempre con motivo. Nada se borra ni se sobrescribe en silencio: el intento fallido
 * se conserva, la corrección de asistencia guarda el valor anterior y cada excepción queda con
 * responsable y fecha. No existe forma de editar una nota ni el progreso directamente.
 *
 * <p>Un certificado ya emitido queda congelado: no se concede un intento CALIFICADO adicional y
 * las correcciones posteriores no recalculan su nota ni su nivel.
 */
@Service
@RequiredArgsConstructor
public class SeguimientoAcademicoService {

    private final MatriculaRepository matriculas;
    private final LeccionRepository lecciones;
    private final ProgresoLeccionRepository progresoLecciones;
    private final AsistenciaRepository asistencias;
    private final ExcepcionAcademicaRepository excepciones;
    private final CertificadoRepository certificados;
    private final UsuarioRepository usuarios;
    private final AvanceAcademicoService avanceAcademico;
    private final AdministrarSesionesService administrarSesiones;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SeguimientoAcademicoResponse consultar(Long matriculaId) {
        exigirAdministrador();
        Matricula matricula = matriculaDe(matriculaId);
        AvanceAcademico avance = avanceAcademico.calcular(matricula);
        RutaAcademica ruta = avance.ruta();
        Instant ahora = clock.instant();

        List<ModuloSeguimiento> modulos = new ArrayList<>();
        Map<Long, ProgresoLeccion> progreso = progresoLecciones.findByMatricula_Id(matricula.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLeccion().getId(), p -> p, (a, b) -> a));
        for (Modulo modulo : ruta.modulos()) {
            List<LeccionSeguimiento> filas = new ArrayList<>();
            for (Leccion leccion : ruta.leccionesPorModulo().get(modulo.getId())) {
                ProgresoLeccion p = progreso.get(leccion.getId());
                boolean completada = Boolean.TRUE.equals(ruta.completadas().get(leccion.getId()));
                String motivo = completada ? null : ruta.motivoBloqueo(leccion.getId());
                String estado = "CANCELADA".equals(leccion.getEstado()) && !completada ? "CANCELADA"
                        : completada ? "COMPLETADA" : motivo != null ? "BLOQUEADA" : "DISPONIBLE";
                filas.add(new LeccionSeguimiento(leccion.getId(), leccion.getTitulo(), leccion.getTipo(),
                        leccion.isEsObligatoria(), estado, p == null ? null : p.getFormaCompletado(),
                        p == null ? null : p.getCompletadaEn(), motivo));
            }
            modulos.add(new ModuloSeguimiento(modulo.getId(), modulo.getTitulo(), filas));
        }

        List<ExamenSeguimiento> examenes = ruta.estadoExamenes().values().stream()
                .map(e -> examenDe(e)).toList();

        List<SesionSeguimiento> sesiones = "VIRTUAL".equals(matricula.getCurso().getModalidad()) ? List.of()
                : sesionesDe(matricula, ahora);

        CertificadoSeguimiento certificado = certificados.findByLogro_Matricula_Id(matricula.getId())
                .map(c -> new CertificadoSeguimiento(c.getCodigo(), c.getEstado(), c.getEmitidoEn())).orElse(null);

        List<ExcepcionAcademica> historial = excepciones.findByMatricula_IdOrderByRealizadoEnDesc(matricula.getId());
        Map<Long, String> responsables = usuarios
                .findAllById(historial.stream().map(ExcepcionAcademica::getRealizadoPorUsuarioId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(u -> u.getId(), u -> u.getPersona().nombreCompleto()));
        List<ExcepcionResponse> excepcionesDto = historial.stream().map(e -> new ExcepcionResponse(e.getId(),
                e.getTipo(), e.getReferenciaTipo(), e.getValorAnterior(), e.getValorNuevo(), e.getMotivo(),
                responsables.getOrDefault(e.getRealizadoPorUsuarioId(), "—"), e.getRealizadoEn())).toList();

        return new SeguimientoAcademicoResponse(matricula.getId(), matricula.getCurso().getId(),
                matricula.getCurso().getTitulo(), matricula.getCurso().getModalidad(), matricula.getEstado(),
                matricula.getUsuario().getPersona().nombreCompleto(), matricula.getUsuario().getCorreo(),
                matricula.getFechaMatricula(), avance.porcentaje(), avance.condiciones(), avance.cumpleTodo(),
                avance.frase(), avance.notaFinal(), avance.asistencia(), avance.asistenciaExigida(), modulos,
                examenes, sesiones, certificado, excepcionesDto);
    }

    /** Concede exactamente un intento adicional: no toca los intentos existentes ni la nota. */
    @Transactional
    public void otorgarIntento(Long matriculaId, OtorgarIntentoRequest peticion) {
        exigirAdministrador();
        Matricula matricula = matriculaDe(matriculaId);
        if ("CANCELADA".equals(matricula.getEstado())) {
            throw new BusinessValidationException("La matrícula está cancelada: no se otorgan intentos.");
        }
        RutaAcademica ruta = avanceAcademico.calcular(matricula).ruta();
        EstadoExamen estado = ruta.estadoExamenes().get(peticion.examenId());
        if (estado == null) {
            throw new ResourceNotFoundException("El examen no pertenece al curso de esta matrícula.");
        }
        String motivoNo = motivoNoPuedeOtorgar(estado);
        if (motivoNo != null) {
            throw new BusinessValidationException(motivoNo);
        }

        ExcepcionAcademica excepcion = new ExcepcionAcademica();
        excepcion.setMatricula(matricula);
        excepcion.setTipo(ExcepcionAcademica.REABRIR_INTENTO);
        excepcion.setReferenciaTipo("EXAMEN");
        excepcion.setReferenciaId(estado.examen().getId());
        excepcion.setValorAnterior(estado.permitidos() + " intentos");
        excepcion.setValorNuevo((estado.permitidos() + 1) + " intentos");
        excepcion.setMotivo(peticion.motivo().strip());
        excepcion.setRealizadoPorUsuarioId(actual.get().userId());
        excepcion.setRealizadoEn(clock.instant());
        excepciones.save(excepcion);
    }

    /** Corrige la asistencia de UNA sesión del alumno indicado; delega en HU-027, que conserva el valor anterior. */
    @Transactional
    public void corregirAsistencia(Long matriculaId, Long asistenciaId, CorregirAsistenciaRequest peticion) {
        exigirAdministrador();
        Matricula matricula = matriculaDe(matriculaId);
        Asistencia asistencia = asistencias.findById(asistenciaId)
                .filter(a -> a.getMatricula().getId().equals(matricula.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("La asistencia no pertenece a esta matrícula."));
        if (asistencia.getLeccion().getFechaHoraFin() == null
                || asistencia.getLeccion().getFechaHoraFin().isAfter(clock.instant())) {
            throw new BusinessValidationException("Solo se corrige la asistencia de una sesión ya realizada.");
        }
        administrarSesiones.corregirAsistencia(asistenciaId, peticion);
    }

    // ------------------------------------------------------------------ helpers --

    private ExamenSeguimiento examenDe(EstadoExamen estado) {
        Examen examen = estado.examen();
        List<IntentoResumenResponse> intentos = estado.intentos().stream()
                .map(i -> new IntentoResumenResponse(i.getId(), i.getNumeroIntento(), i.getEstado(), i.getNota(),
                        i.getAprobado(), i.getIniciadoEn(), i.getEnviadoEn(),
                        i.estaPendienteRevision() && i.getEnviadoEn() != null
                                ? i.getEnviadoEn().plus(examen.getDiasRevision(), ChronoUnit.DAYS) : null))
                .toList();
        String motivoNo = motivoNoPuedeOtorgar(estado);
        return new ExamenSeguimiento(examen.getId(), examen.getTitulo(), examen.getTipo(), examen.getFinalidad(),
                examen.getModulo() == null ? null : examen.getModulo().getTitulo(), estado.codigo(), estado.usados(),
                estado.permitidos(), estado.mejorNota(), estado.aprobado(), estado.agotadoSinAprobar(),
                motivoNo == null, motivoNo, intentos);
    }

    /** {@code null} si se puede conceder un intento adicional; si no, la causa en palabras. */
    private static String motivoNoPuedeOtorgar(EstadoExamen estado) {
        if (!estado.esCalificado()) {
            return "Los exámenes de práctica no tienen límite de intentos.";
        }
        if (estado.certificadoEmitido()) {
            return "El certificado ya fue emitido: no se otorgan nuevos intentos calificados.";
        }
        if (estado.permitidos() == null) {
            return "Este examen tiene intentos ilimitados.";
        }
        if (estado.enProgreso() != null || estado.pendienteRevision() != null) {
            return "Hay un intento en curso o pendiente de revisión: espera a que termine.";
        }
        if (estado.aprobado()) {
            return "El alumno ya aprobó este examen.";
        }
        if (!estado.agotado()) {
            return "El alumno todavía tiene intentos disponibles.";
        }
        return null;
    }

    private List<SesionSeguimiento> sesionesDe(Matricula matricula, Instant ahora) {
        Map<Long, Asistencia> porLeccion = asistencias.findByMatricula_Id(matricula.getId()).stream()
                .collect(Collectors.toMap(a -> a.getLeccion().getId(), a -> a, (a, b) -> a));
        Instant desde = matricula.getFechaMatricula();
        List<SesionSeguimiento> filas = new ArrayList<>();
        for (Leccion leccion : lecciones.buscarSesionesEnVivoDeCurso(matricula.getCurso().getId())) {
            Asistencia a = porLeccion.get(leccion.getId());
            boolean cancelada = "CANCELADA".equals(leccion.getEstado());
            boolean elegible = !cancelada && (desde == null
                    || (leccion.getFechaHoraInicio() != null && leccion.getFechaHoraInicio().isAfter(desde)));
            boolean realizada = leccion.getFechaHoraFin() != null && !leccion.getFechaHoraFin().isAfter(ahora);
            String estadoSesion = cancelada ? "CANCELADA" : realizada ? "REALIZADA" : "PROGRAMADA";
            filas.add(new SesionSeguimiento(leccion.getId(), a == null ? null : a.getId(), leccion.getTitulo(),
                    leccion.getFechaHoraInicio(), estadoSesion, a == null ? "SIN_REGISTRO" : a.getEstado(),
                    a == null ? null : a.getMotivoCorreccion(), elegible, a != null && realizada && !cancelada));
        }
        filas.sort(Comparator.comparing(SesionSeguimiento::inicio, Comparator.nullsLast(Comparator.naturalOrder())));
        return filas;
    }

    private Matricula matriculaDe(Long matriculaId) {
        return matriculas.findById(matriculaId)
                .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
