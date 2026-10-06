package pe.edu.utp.escuela.app.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CerrarCursoPeticion;
import pe.edu.utp.escuela.app.dto.CursoEditorRespuesta;
import pe.edu.utp.escuela.app.dto.RetrasarInicioPeticion;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.CursoDocente;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.HistorialEstadoCurso;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CursoDocenteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.EstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.HistorialEstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-016 — Transiciones ordinarias del ciclo de vida (BORRADOR ya cubierto en HU-010, PUBLICADO
 * en HU-015): adelantar/retrasar el inicio, cerrar anticipadamente, destacar y duplicar como una
 * nueva convocatoria independiente. CANCELADO pertenece a HU-038 (EP06): no se reconoce, ejecuta
 * ni demuestra aquí. Las transiciones automáticas por fecha corren en {@link
 * #aplicarTransicionesAutomaticas()}, un job diario sin usuario autenticado. */
@Service
@RequiredArgsConstructor
public class CicloVidaCursoServicio {

    private final CursoRepositorio cursos;
    private final EstadoCursoRepositorio estadosCurso;
    private final HistorialEstadoCursoRepositorio historial;
    private final ReglaCursoRepositorio reglasCurso;
    private final CursoDocenteRepositorio cursoDocentes;
    private final ModuloRepositorio modulos;
    private final CurrentUserService currentUserService;
    private final TextNormalizer textos;
    private final Clock clock;
    private final CursoServicio cursoServicio;
    private final ContenidoServicio contenidoServicio;
    private final ExamenServicio examenServicio;

    @Transactional
    public CursoEditorRespuesta adelantarInicio(Long cursoId) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        requerirEstado(curso, "PUBLICADO", "Solo un curso publicado puede adelantar su inicio.");

        LocalDate hoy = LocalDate.now(clock);
        if (curso.getFechaInicio() == null || curso.getFechaInicio().isAfter(hoy)) {
            curso.setFechaInicio(hoy);
        }
        transicionar(curso, "EN_CURSO", "Inicio adelantado por administración.", currentUserService.get().userId());
        return cursoServicio.obtener(cursoId);
    }

    @Transactional
    public CursoEditorRespuesta retrasarInicio(Long cursoId, RetrasarInicioPeticion p) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        requerirEstado(curso, "PUBLICADO", "Solo antes de iniciar puedes retrasar la fecha de inicio.");
        if (!p.nuevaFechaInicio().isAfter(curso.getFechaInicio())) {
            throw new BusinessValidationException("La nueva fecha debe ser posterior a la fecha de inicio actual.");
        }
        if (curso.getFechaFin() != null && p.nuevaFechaInicio().isAfter(curso.getFechaFin())) {
            throw new BusinessValidationException("La nueva fecha de inicio no puede ser posterior a la fecha de fin.");
        }
        // No es un cambio de estado: no se registra en historial_estado_curso. El impacto en
        // matrículas ya existentes se refleja en "tieneMatriculas" de la respuesta (advertencia,
        // no bloqueo: retrasar antes de iniciar siempre está permitido).
        curso.setFechaInicio(p.nuevaFechaInicio());
        return cursoServicio.obtener(cursoId);
    }

    @Transactional
    public CursoEditorRespuesta cerrar(Long cursoId, CerrarCursoPeticion p) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        String estadoActual = curso.getEstadoCurso().getCodigo();
        if (!"PUBLICADO".equals(estadoActual) && !"EN_CURSO".equals(estadoActual)) {
            throw new BusinessValidationException("Solo un curso publicado o en curso puede cerrarse.");
        }
        String motivo = textos.trimToNull(p.motivo());
        transicionar(curso, "CERRADO", motivo != null ? motivo : "Cierre anticipado por administración.",
                currentUserService.get().userId());
        return cursoServicio.obtener(cursoId);
    }

    @Transactional
    public CursoEditorRespuesta cambiarDestacado(Long cursoId, boolean destacado) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        curso.setDestacado(destacado);
        return cursoServicio.obtener(cursoId);
    }

    /** HU-016 — Nueva convocatoria BORRADOR independiente: copia información general, módulos,
     * lecciones, materiales, exámenes, preguntas, opciones, reglas de certificación, docentes y
     * beneficios. No copia matrículas, pagos, progreso, intentos, asistencia ni certificados (esas
     * tablas no tienen referencia al curso de origen porque simplemente no existe nada que copiar:
     * la copia nace sin ellas). Tampoco copia firmantes: no están en la lista de la historia y
     * conviene revisarlos para cada convocatoria. */
    @Transactional
    public CursoEditorRespuesta duplicar(Long cursoId) {
        exigirAdministrador();
        Curso origen = buscarOLanzar(cursoId);
        EstadoCurso borrador = estadosCurso.findByCodigo("BORRADOR")
                .orElseThrow(() -> new IllegalStateException("Falta el estado BORRADOR"));

        String tituloCopia = origen.getTitulo() + " (copia)";
        Curso copia = new Curso();
        copia.setCursoOrigenId(origen.getId());
        copia.setTitulo(tituloCopia);
        copia.setUrlAmigable(generarUrlAmigableUnica(tituloCopia));
        copia.setEstadoCurso(borrador);
        copia.setDescripcion(origen.getDescripcion());
        copia.setImagenPortadaUrl(origen.getImagenPortadaUrl());
        copia.setTipoCurso(origen.getTipoCurso());
        copia.setCategoriaTematica(origen.getCategoriaTematica());
        copia.setEntidadCertificadora(origen.getEntidadCertificadora());
        copia.setModalidad(origen.getModalidad());
        copia.setTipoVenta(origen.getTipoVenta());
        copia.setDestacado(false);
        copia.setPrecioRegular(origen.getPrecioRegular());
        copia.setPrecioPromocional(origen.getPrecioPromocional());
        copia.setPromocionInicioEn(origen.getPromocionInicioEn());
        copia.setPromocionFinEn(origen.getPromocionFinEn());
        // Se copian como punto de partida; HU-015 bloqueará publicar hasta que se revisen (p. ej.
        // una fecha de inicio ya pasada), tal como pide la historia.
        copia.setFechaInicio(origen.getFechaInicio());
        copia.setFechaFin(origen.getFechaFin());
        copia.setFechaCierreMatricula(origen.getFechaCierreMatricula());
        copia.setCupoMaximo(origen.getCupoMaximo());
        copia.setHorasAcademicas(origen.getHorasAcademicas());
        copia.setVigenciaAccesoDias(origen.getVigenciaAccesoDias());
        copia.setBeneficios(origen.getBeneficios().clone());
        copia.setCreadoPorUsuarioId(currentUserService.get().userId());
        cursos.saveAndFlush(copia);

        ReglaCurso reglaOrigen = reglasCurso.findByCurso_Id(origen.getId())
                .orElseThrow(() -> new IllegalStateException("El curso de origen no tiene reglas."));
        ReglaCurso reglaCopia = new ReglaCurso();
        reglaCopia.setCurso(copia);
        reglaCopia.setRequiereExamenes(reglaOrigen.isRequiereExamenes());
        reglaCopia.setRequiereProgreso(reglaOrigen.isRequiereProgreso());
        reglaCopia.setRequiereAsistencia(reglaOrigen.isRequiereAsistencia());
        reglaCopia.setNotaMinima(reglaOrigen.getNotaMinima());
        reglaCopia.setNotaRefrendado(reglaOrigen.getNotaRefrendado());
        reglaCopia.setProgresoMinimo(reglaOrigen.getProgresoMinimo());
        reglaCopia.setUmbralVideo(reglaOrigen.getUmbralVideo());
        reglaCopia.setAsistenciaMinima(reglaOrigen.getAsistenciaMinima());
        reglaCopia.setSecuenciaObligatoria(reglaOrigen.isSecuenciaObligatoria());
        reglaCopia.setDiasEsperaCertificado(reglaOrigen.getDiasEsperaCertificado());
        // bloqueadoEn queda null: es una convocatoria nueva, todavía sin congelar.
        reglasCurso.saveAndFlush(reglaCopia);

        int orden = 1;
        for (CursoDocente cd : cursoDocentes.findByCurso_IdOrderByOrdenAsc(origen.getId())) {
            CursoDocente nuevaAsignacion = new CursoDocente();
            nuevaAsignacion.setCurso(copia);
            nuevaAsignacion.setPersona(cd.getPersona());
            nuevaAsignacion.setOrden(orden++);
            cursoDocentes.save(nuevaAsignacion);
        }

        for (Modulo moduloOrigen : modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(origen.getId())) {
            contenidoServicio.copiarModulo(copia.getId(), moduloOrigen.getId());
        }
        examenServicio.copiarExamenesFinales(copia.getId(), origen.getId());

        HistorialEstadoCurso h = new HistorialEstadoCurso();
        h.setCurso(copia);
        h.setEstadoAnterior(null);
        h.setEstadoNuevo(borrador);
        h.setMotivo("Duplicado desde \"" + origen.getTitulo() + "\" (curso #" + origen.getId() + ").");
        h.setRealizadoPorUsuarioId(currentUserService.get().userId());
        h.setRealizadoEn(clock.instant());
        historial.saveAndFlush(h);

        return cursoServicio.obtener(copia.getId());
    }

    /** HU-016 — Transiciones que no dependen de una acción administrativa: PUBLICADO pasa a
     * EN_CURSO al llegar su fecha de inicio (incluye VIRTUAL con fecha); EN_CURSO pasa a CERRADO al
     * pasar su fecha de fin (nunca aplica a VIRTUAL, que no tiene fecha de fin y solo cierra
     * manualmente). Sin usuario autenticado: no llama exigirAdministrador ni currentUserService. */
    @Transactional
    @Scheduled(cron = "0 5 0 * * *", zone = "${application.time-zone}")
    public void aplicarTransicionesAutomaticas() {
        LocalDate hoy = LocalDate.now(clock);
        for (Curso curso : cursos.findByEstadoCurso_CodigoAndFechaInicioLessThanEqual("PUBLICADO", hoy)) {
            transicionar(curso, "EN_CURSO", "Inicio automático al llegar la fecha.", null);
        }
        for (Curso curso : cursos.findByEstadoCurso_CodigoAndFechaFinLessThan("EN_CURSO", hoy)) {
            transicionar(curso, "CERRADO", "Cierre automático al finalizar la convocatoria.", null);
        }
    }

    // ---------------------------------------------------------------- Soporte --

    private void transicionar(Curso curso, String codigoDestino, String motivo, Long usuarioId) {
        EstadoCurso anterior = curso.getEstadoCurso();
        EstadoCurso destino = estadosCurso.findByCodigo(codigoDestino)
                .orElseThrow(() -> new IllegalStateException("Falta el estado " + codigoDestino));
        curso.setEstadoCurso(destino);
        if ("CERRADO".equals(codigoDestino)) {
            curso.setCerradoEn(clock.instant());
        }

        HistorialEstadoCurso h = new HistorialEstadoCurso();
        h.setCurso(curso);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(destino);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(usuarioId);
        h.setRealizadoEn(clock.instant());
        historial.saveAndFlush(h);
    }

    private void requerirEstado(Curso curso, String codigoEsperado, String mensajeError) {
        if (!codigoEsperado.equals(curso.getEstadoCurso().getCodigo())) {
            throw new BusinessValidationException(mensajeError);
        }
    }

    private String generarUrlAmigableUnica(String titulo) {
        String sinAcentos = Normalizer.normalize(titulo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String base = sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "curso";
        }
        String candidato = base;
        int sufijo = 2;
        while (cursos.existsByUrlAmigable(candidato)) {
            candidato = base + "-" + sufijo++;
        }
        return candidato;
    }

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private Curso buscarOLanzar(Long cursoId) {
        return cursos.findWithDetalleById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso ya no existe."));
    }
}
