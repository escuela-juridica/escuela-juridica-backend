package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.DuracionLeccionFila;
import pe.edu.utp.escuela.app.dto.ErrorValidacionCurso;
import pe.edu.utp.escuela.app.dto.ModalidadCurso;
import pe.edu.utp.escuela.app.dto.TipoVentaCurso;
import pe.edu.utp.escuela.app.dto.ValidacionPublicacionRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.CursoDocente;
import pe.edu.utp.escuela.app.entity.CursoFirmante;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.HistorialEstadoCurso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Pregunta;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CursoDocenteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoFirmanteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.EstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.ExamenRepositorio;
import pe.edu.utp.escuela.app.repository.HistorialEstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.PreguntaRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/** HU-015 — Valida integralmente un BORRADOR antes de publicarlo. Reúne todos los hallazgos de una
 * vez (nunca lanza en el primer error) para que Angular muestre la lista completa y el
 * administrador corrija todo junto, en lugar de una vuelta por cada campo. */
@Service
@RequiredArgsConstructor
public class PublicacionServicio {

    private static final String SEV_ERROR = "ERROR";
    private static final String SEV_ADVERTENCIA = "ADVERTENCIA";
    private static final int DURACION_MINIMA_RECOMENDADA_MIN = 10;
    private static final int DURACION_MAXIMA_RECOMENDADA_MIN = 15;

    private final CursoRepositorio cursos;
    private final ModuloRepositorio modulos;
    private final LeccionRepositorio lecciones;
    private final MaterialLeccionRepositorio materiales;
    private final ExamenRepositorio examenes;
    private final PreguntaRepositorio preguntas;
    private final ReglaCursoRepositorio reglasCurso;
    private final CursoFirmanteRepositorio cursoFirmantes;
    private final CursoDocenteRepositorio cursoDocentes;
    private final EstadoCursoRepositorio estadosCurso;
    private final HistorialEstadoCursoRepositorio historial;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ValidacionPublicacionRespuesta validar(Long cursoId) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        if (!"BORRADOR".equals(curso.getEstadoCurso().getCodigo())) {
            return new ValidacionPublicacionRespuesta(false, List.of(), false, curso.getEstadoCurso().getCodigo());
        }
        List<ErrorValidacionCurso> hallazgos = evaluar(curso);
        return new ValidacionPublicacionRespuesta(puedePublicarse(hallazgos), hallazgos, false,
                curso.getEstadoCurso().getCodigo());
    }

    @Transactional
    public ValidacionPublicacionRespuesta publicar(Long cursoId) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        if (!"BORRADOR".equals(curso.getEstadoCurso().getCodigo())) {
            // Doble solicitud (o un ciclo de vida ya avanzado, HU-016): no hay nada que repetir.
            return new ValidacionPublicacionRespuesta(false, List.of(), false, curso.getEstadoCurso().getCodigo());
        }

        List<ErrorValidacionCurso> hallazgos = evaluar(curso);
        if (!puedePublicarse(hallazgos)) {
            return new ValidacionPublicacionRespuesta(false, hallazgos, false, curso.getEstadoCurso().getCodigo());
        }

        ModalidadCurso modalidad = ModalidadCurso.valueOf(curso.getModalidad());
        boolean directoAEnCurso = modalidad == ModalidadCurso.VIRTUAL && curso.getFechaInicio() == null;
        String codigoDestino = directoAEnCurso ? "EN_CURSO" : "PUBLICADO";
        EstadoCurso destino = estadosCurso.findByCodigo(codigoDestino)
                .orElseThrow(() -> new IllegalStateException("Falta el estado " + codigoDestino));

        EstadoCurso anterior = curso.getEstadoCurso();
        curso.setEstadoCurso(destino);
        curso.setPublicadoEn(clock.instant());
        registrarHistorial(curso, anterior, destino, directoAEnCurso
                ? "Publicado: al ser virtual sin fecha de inicio, queda EN_CURSO de inmediato."
                : "Publicado.");

        return new ValidacionPublicacionRespuesta(true, hallazgos, true, destino.getCodigo());
    }

    private boolean puedePublicarse(List<ErrorValidacionCurso> hallazgos) {
        return hallazgos.stream().noneMatch(h -> SEV_ERROR.equals(h.severidad()));
    }

    // ------------------------------------------------------------------ Evaluación --

    private List<ErrorValidacionCurso> evaluar(Curso curso) {
        List<ErrorValidacionCurso> hallazgos = new ArrayList<>();

        ModalidadCurso modalidad = evaluarInformacionBasica(curso, hallazgos);
        TipoVentaCurso tipoVenta = curso.getTipoVenta() == null ? null : TipoVentaCurso.valueOf(curso.getTipoVenta());
        evaluarComercial(curso, tipoVenta, hallazgos);

        List<Modulo> modulosActivos = modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(curso.getId());
        List<Long> moduloIds = modulosActivos.stream().map(Modulo::getId).toList();
        List<Leccion> leccionesActivas = moduloIds.isEmpty() ? List.of() : lecciones.buscarActivasDeModulos(moduloIds);
        List<Long> leccionIds = leccionesActivas.stream().map(Leccion::getId).toList();

        Set<Long> leccionIdsConContenido = leccionIds.isEmpty() ? Set.of()
                : materiales.findByLeccion_IdInOrderByLeccion_IdAscOrdenAsc(leccionIds).stream()
                        .filter(ml -> ml.isActivo() && ml.getRecurso().isActivo())
                        .map(ml -> ml.getLeccion().getId())
                        .collect(Collectors.toSet());

        List<Leccion> leccionesCursables = leccionesActivas.stream()
                .filter(l -> l.isEsObligatoria() && esLeccionCompletable(l, leccionIdsConContenido))
                .toList();
        if (modulosActivos.isEmpty() || leccionesCursables.isEmpty()) {
            error(hallazgos, "CONTENIDO", "modulos",
                    "El curso necesita al menos un módulo con una lección obligatoria que pueda cursarse.");
        }

        if (tipoVenta == TipoVentaCurso.PAGADO) {
            boolean hayPreviewReal = leccionesActivas.stream().anyMatch(l ->
                    l.isEsVistaPrevia() && "GRABADA".equals(l.getTipo()) && leccionIdsConContenido.contains(l.getId()));
            if (!hayPreviewReal) {
                error(hallazgos, "CONTENIDO", "vistaPrevia",
                        "Un curso pagado necesita al menos una lección grabada de vista previa con contenido real.");
            }
        }

        evaluarDuracionVideo(leccionesActivas, hallazgos);

        if (modalidad != null) {
            evaluarSesiones(modalidad, leccionesActivas, leccionIdsConContenido, hallazgos);
        }

        List<Examen> examenesActivos = examenes.findByCurso_IdOrderByOrdenAsc(curso.getId()).stream()
                .filter(Examen::isActivo)
                .toList();
        ReglaCurso regla = reglasCurso.findByCurso_Id(curso.getId()).orElse(null);
        evaluarExamenes(regla, examenesActivos, hallazgos);
        if (regla != null && modalidad != null) {
            evaluarReglas(modalidad, regla, leccionesCursables, leccionesActivas, hallazgos);
        }

        evaluarCertificado(curso, hallazgos);
        evaluarDocentes(curso, hallazgos);

        return hallazgos;
    }

    private boolean esLeccionCompletable(Leccion l, Set<Long> leccionIdsConContenido) {
        if ("EN_VIVO".equals(l.getTipo())) {
            return sesionCompleta(l);
        }
        return leccionIdsConContenido.contains(l.getId());
    }

    private boolean sesionCompleta(Leccion l) {
        return l.getFechaHoraInicio() != null && l.getFechaHoraFin() != null && l.getEnlaceReunion() != null;
    }

    private ModalidadCurso evaluarInformacionBasica(Curso curso, List<ErrorValidacionCurso> hallazgos) {
        if (curso.getTitulo() == null || curso.getTitulo().isBlank()) {
            error(hallazgos, "INFORMACION", "titulo", "Falta el título del curso.");
        }
        if (curso.getDescripcion() == null || curso.getDescripcion().isBlank()) {
            error(hallazgos, "INFORMACION", "descripcion", "Falta la descripción del curso.");
        }
        if (curso.getTipoCurso() == null) {
            error(hallazgos, "INFORMACION", "tipoCurso", "Falta el tipo de curso.");
        } else if (!curso.getTipoCurso().isActivo()) {
            error(hallazgos, "INFORMACION", "tipoCurso", "El tipo de curso asignado ya no está activo.");
        }
        if (curso.getCategoriaTematica() == null) {
            error(hallazgos, "INFORMACION", "categoriaTematica", "Falta la categoría temática.");
        } else if (!curso.getCategoriaTematica().isActivo()) {
            error(hallazgos, "INFORMACION", "categoriaTematica", "La categoría temática asignada ya no está activa.");
        }
        if (curso.getHorasAcademicas() == null || curso.getHorasAcademicas().signum() <= 0) {
            error(hallazgos, "INFORMACION", "horasAcademicas", "Las horas académicas deben ser mayores que cero.");
        }

        if (curso.getModalidad() == null) {
            error(hallazgos, "INFORMACION", "modalidad", "Falta la modalidad del curso.");
            return null;
        }
        ModalidadCurso modalidad = ModalidadCurso.valueOf(curso.getModalidad());
        evaluarFechas(curso, modalidad, hallazgos);
        return modalidad;
    }

    private void evaluarFechas(Curso curso, ModalidadCurso modalidad, List<ErrorValidacionCurso> hallazgos) {
        if (modalidad == ModalidadCurso.VIRTUAL) {
            if (curso.getFechaFin() != null) {
                error(hallazgos, "FECHAS", "fechaFin", "Un curso virtual no admite fecha de fin.");
            }
            return;
        }
        if (curso.getFechaInicio() == null || curso.getFechaFin() == null) {
            error(hallazgos, "FECHAS", "fechas", "La modalidad EN_VIVO o HIBRIDO exige fecha de inicio y de fin.");
            return;
        }
        if (curso.getFechaFin().isBefore(curso.getFechaInicio())) {
            error(hallazgos, "FECHAS", "fechaFin", "La fecha de fin no puede ser anterior a la de inicio.");
        }
        if (curso.getFechaCierreMatricula() != null && curso.getFechaCierreMatricula().isAfter(curso.getFechaFin())) {
            error(hallazgos, "FECHAS", "fechaCierreMatricula",
                    "El cierre de matrícula no puede ser posterior a la fecha de fin.");
        }
    }

    private void evaluarComercial(Curso curso, TipoVentaCurso tipoVenta, List<ErrorValidacionCurso> hallazgos) {
        if (tipoVenta == null) {
            error(hallazgos, "COMERCIAL", "tipoVenta", "Falta el tipo de venta.");
            return;
        }
        BigDecimal regular = curso.getPrecioRegular();
        if (regular == null || regular.signum() < 0) {
            error(hallazgos, "COMERCIAL", "precioRegular", "El precio regular no puede ser negativo.");
        } else if (tipoVenta == TipoVentaCurso.GRATUITO && regular.signum() != 0) {
            error(hallazgos, "COMERCIAL", "precioRegular", "Un curso gratuito no puede tener precio regular.");
        }
        BigDecimal promocional = curso.getPrecioPromocional();
        if (promocional != null && regular != null) {
            if (promocional.signum() < 0) {
                error(hallazgos, "COMERCIAL", "precioPromocional", "El precio promocional no puede ser negativo.");
            } else if (promocional.compareTo(regular) > 0) {
                error(hallazgos, "COMERCIAL", "precioPromocional",
                        "El precio promocional no puede ser mayor que el precio regular.");
            }
        }
        Instant promoInicio = curso.getPromocionInicioEn();
        Instant promoFin = curso.getPromocionFinEn();
        boolean algunaFechaPromo = promoInicio != null || promoFin != null;
        if (algunaFechaPromo) {
            if (promoInicio == null || promoFin == null) {
                error(hallazgos, "COMERCIAL", "promocion", "La vigencia de la promoción exige inicio y fin.");
            } else if (promoInicio.isAfter(promoFin)) {
                error(hallazgos, "COMERCIAL", "promocion", "El inicio de la promoción no puede ser posterior a su fin.");
            }
        }
        if (curso.getCupoMaximo() != null && curso.getCupoMaximo() <= 0) {
            error(hallazgos, "COMERCIAL", "cupoMaximo", "La capacidad debe ser mayor que cero.");
        }
        if (curso.getVigenciaAccesoDias() != null && curso.getVigenciaAccesoDias() <= 0) {
            error(hallazgos, "COMERCIAL", "vigenciaAccesoDias", "La vigencia de acceso debe ser mayor que cero días.");
        }
    }

    private void evaluarDuracionVideo(List<Leccion> leccionesActivas, List<ErrorValidacionCurso> hallazgos) {
        List<Leccion> grabadas = leccionesActivas.stream().filter(l -> "GRABADA".equals(l.getTipo())).toList();
        if (grabadas.isEmpty()) {
            return;
        }
        Map<Long, String> tituloPorLeccion = grabadas.stream()
                .collect(Collectors.toMap(Leccion::getId, Leccion::getTitulo));
        List<Long> idsGrabadas = grabadas.stream().map(Leccion::getId).toList();

        // Recorre todos los videos de la lista, no solo el primero fuera de rango: así el
        // administrador ve de una vez cuáles lecciones necesitan ajustar su duración, no una por
        // revisión.
        for (DuracionLeccionFila fila : materiales.buscarDuraciones(idsGrabadas)) {
            Integer segundos = fila.getDuracionSegundos();
            if (segundos == null) {
                continue;
            }
            int minutos = segundos / 60;
            if (minutos < DURACION_MINIMA_RECOMENDADA_MIN || minutos > DURACION_MAXIMA_RECOMENDADA_MIN) {
                String titulo = tituloPorLeccion.getOrDefault(fila.getLeccionId(), "Lección");
                advertencia(hallazgos, "CONTENIDO", "duracionVideo",
                        "El video de la lección \"" + titulo + "\" dura fuera del rango editorial recomendado de 10 a 15 minutos.");
            }
        }
    }

    private void evaluarSesiones(
            ModalidadCurso modalidad, List<Leccion> leccionesActivas, Set<Long> leccionIdsConContenido,
            List<ErrorValidacionCurso> hallazgos) {
        List<Leccion> sesiones = leccionesActivas.stream().filter(l -> "EN_VIVO".equals(l.getTipo())).toList();
        if (modalidad == ModalidadCurso.VIRTUAL) {
            if (!sesiones.isEmpty()) {
                error(hallazgos, "SESIONES", "modalidad", "Un curso virtual no puede tener lecciones en vivo.");
            }
            return;
        }

        Instant ahora = clock.instant();
        boolean haySesionFuturaValida = sesiones.stream()
                .anyMatch(l -> sesionCompleta(l) && l.getFechaHoraInicio().isAfter(ahora));
        if (!haySesionFuturaValida) {
            error(hallazgos, "SESIONES", "sesiones",
                    "La modalidad exige al menos una sesión en vivo futura, completa (fecha, hora y enlace).");
        }

        if (modalidad == ModalidadCurso.HIBRIDO) {
            boolean hayGrabadaConContenido = leccionesActivas.stream()
                    .anyMatch(l -> "GRABADA".equals(l.getTipo()) && leccionIdsConContenido.contains(l.getId()));
            if (!hayGrabadaConContenido) {
                error(hallazgos, "SESIONES", "contenidoGrabado",
                        "Un curso híbrido necesita además contenido grabado, no solo sesiones en vivo.");
            }
        }
    }

    private void evaluarExamenes(ReglaCurso regla, List<Examen> examenesActivos, List<ErrorValidacionCurso> hallazgos) {
        boolean requiereExamenes = regla != null && regla.isRequiereExamenes();
        List<Examen> calificados = examenesActivos.stream().filter(e -> "CALIFICADO".equals(e.getTipo())).toList();

        if (!requiereExamenes) {
            if (!calificados.isEmpty()) {
                error(hallazgos, "EXAMENES", "tipo",
                        "No puede haber exámenes CALIFICADO mientras no exija exámenes para certificar.");
            }
            return;
        }
        if (calificados.isEmpty()) {
            error(hallazgos, "EXAMENES", "calificados", "Si exige exámenes, debe haber al menos un examen CALIFICADO.");
            return;
        }
        for (Examen examen : calificados) {
            List<Pregunta> propias = preguntas.findByExamen_IdOrderByOrdenAsc(examen.getId()).stream()
                    .filter(Pregunta::isActivo)
                    .toList();
            boolean completo = !propias.isEmpty()
                    && propias.stream().allMatch(p -> p.getPuntaje() != null && p.getPuntaje().signum() > 0);
            if (!completo) {
                error(hallazgos, "EXAMENES", "preguntas",
                        "El examen \"" + examen.getTitulo() + "\" necesita preguntas con puntaje positivo.");
                continue;
            }
            // No es una regla explícita de la historia, pero un examen cuya suma de puntajes no
            // alcanza la nota mínima configurada jamás podría aprobarse: el curso quedaría
            // publicado pero imposible de certificar por esa vía.
            BigDecimal puntajeTotal = propias.stream().map(Pregunta::getPuntaje).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (regla.getNotaMinima() != null && puntajeTotal.compareTo(regla.getNotaMinima()) < 0) {
                error(hallazgos, "EXAMENES", "puntajeTotal",
                        "El examen \"" + examen.getTitulo() + "\" solo puede alcanzar " + puntajeTotal
                                + " puntos en total, por debajo de la nota mínima configurada ("
                                + regla.getNotaMinima() + ").");
            }
        }
    }

    private void evaluarReglas(
            ModalidadCurso modalidad, ReglaCurso regla, List<Leccion> leccionesCursables,
            List<Leccion> leccionesActivas, List<ErrorValidacionCurso> hallazgos) {
        if (regla.isRequiereProgreso() && leccionesCursables.isEmpty()) {
            error(hallazgos, "REGLAS", "progreso",
                    "Si exige progreso, debe existir al menos una lección obligatoria que pueda cursarse.");
        }
        if (regla.isRequiereAsistencia()) {
            if (modalidad == ModalidadCurso.VIRTUAL) {
                error(hallazgos, "REGLAS", "asistencia", "Un curso virtual no puede exigir asistencia.");
            } else {
                Instant ahora = clock.instant();
                boolean haySesionFutura = leccionesActivas.stream()
                        .filter(l -> "EN_VIVO".equals(l.getTipo()))
                        .anyMatch(l -> sesionCompleta(l) && l.getFechaHoraInicio().isAfter(ahora));
                if (!haySesionFutura) {
                    error(hallazgos, "REGLAS", "asistencia",
                            "Si exige asistencia, debe existir al menos una sesión futura completa.");
                }
            }
        }

        rangoPorcentaje(hallazgos, "progresoMinimo", regla.getProgresoMinimo());
        rangoPorcentaje(hallazgos, "umbralVideo", regla.getUmbralVideo());
        rangoPorcentaje(hallazgos, "asistenciaMinima", regla.getAsistenciaMinima());

        BigDecimal notaMinima = regla.getNotaMinima();
        BigDecimal notaRefrendado = regla.getNotaRefrendado();
        if (fueraDeRangoNota(notaMinima)) {
            error(hallazgos, "REGLAS", "notaMinima", "La nota mínima debe estar entre 0 y 20.");
        }
        if (fueraDeRangoNota(notaRefrendado)) {
            error(hallazgos, "REGLAS", "notaRefrendado", "La nota de Refrendado debe estar entre 0 y 20.");
        } else if (regla.isRequiereExamenes() && notaMinima != null && notaRefrendado.compareTo(notaMinima) <= 0) {
            error(hallazgos, "REGLAS", "notaRefrendado", "La nota de Refrendado debe ser mayor que la nota mínima.");
        }
        if (regla.getDiasEsperaCertificado() == null || regla.getDiasEsperaCertificado() < 0) {
            error(hallazgos, "REGLAS", "diasEspera", "Los días de espera del certificado no pueden ser negativos.");
        }
    }

    private boolean fueraDeRangoNota(BigDecimal nota) {
        return nota == null || nota.signum() < 0 || nota.compareTo(BigDecimal.valueOf(20)) > 0;
    }

    private void rangoPorcentaje(List<ErrorValidacionCurso> hallazgos, String campo, BigDecimal valor) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(BigDecimal.valueOf(100)) > 0) {
            error(hallazgos, "REGLAS", campo, "El porcentaje de \"" + campo + "\" debe estar entre 0 y 100.");
        }
    }

    private void evaluarCertificado(Curso curso, List<ErrorValidacionCurso> hallazgos) {
        if (curso.getEntidadCertificadora() == null) {
            error(hallazgos, "CERTIFICADO", "entidadCertificadora", "Falta asignar la entidad certificadora.");
        } else if (!curso.getEntidadCertificadora().isActivo()) {
            error(hallazgos, "CERTIFICADO", "entidadCertificadora",
                    "La entidad certificadora asignada ya no está activa.");
        }

        List<CursoFirmante> firmantesCurso = cursoFirmantes.findByCurso_IdOrderByOrdenAsc(curso.getId());
        if (firmantesCurso.isEmpty()) {
            error(hallazgos, "CERTIFICADO", "firmantes", "El curso necesita al menos un firmante.");
            return;
        }
        for (CursoFirmante cf : firmantesCurso) {
            String nombre = cf.getFirmante().getPersona().nombreCompleto();
            if (!cf.getFirmante().isActivo()) {
                error(hallazgos, "CERTIFICADO", "firmantes", "El firmante \"" + nombre + "\" ya no está activo.");
            } else if (cf.getFirmante().getImagenFirmaUrl() == null || cf.getFirmante().getImagenFirmaUrl().isBlank()) {
                error(hallazgos, "CERTIFICADO", "firmantes", "El firmante \"" + nombre + "\" no tiene imagen de firma.");
            }
        }
    }

    private void evaluarDocentes(Curso curso, List<ErrorValidacionCurso> hallazgos) {
        List<CursoDocente> asignados = cursoDocentes.findByCurso_IdOrderByOrdenAsc(curso.getId());
        if (asignados.isEmpty()) {
            error(hallazgos, "DOCENTES", "docentes", "El curso necesita al menos un docente activo.");
            return;
        }
        long personasUnicas = asignados.stream().map(cd -> cd.getPersona().getId()).distinct().count();
        if (personasUnicas != asignados.size()) {
            error(hallazgos, "DOCENTES", "docentes", "Hay un docente asignado más de una vez.");
        }
        boolean hayActivo = asignados.stream().anyMatch(cd -> cd.getPersona().isActivo());
        if (!hayActivo) {
            error(hallazgos, "DOCENTES", "docentes", "Ningún docente asignado está activo.");
        }
    }

    private void error(List<ErrorValidacionCurso> hallazgos, String seccion, String campo, String mensaje) {
        hallazgos.add(new ErrorValidacionCurso(seccion + ":" + campo, seccion, campo, SEV_ERROR, mensaje));
    }

    private void advertencia(List<ErrorValidacionCurso> hallazgos, String seccion, String campo, String mensaje) {
        hallazgos.add(new ErrorValidacionCurso(seccion + ":" + campo, seccion, campo, SEV_ADVERTENCIA, mensaje));
    }

    // ---------------------------------------------------------------- Soporte --

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private Curso buscarOLanzar(Long cursoId) {
        return cursos.findWithDetalleById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso ya no existe."));
    }

    private void registrarHistorial(Curso curso, EstadoCurso anterior, EstadoCurso nuevo, String motivo) {
        HistorialEstadoCurso h = new HistorialEstadoCurso();
        h.setCurso(curso);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(currentUserService.get().userId());
        h.setRealizadoEn(clock.instant());
        historial.saveAndFlush(h);
    }
}
