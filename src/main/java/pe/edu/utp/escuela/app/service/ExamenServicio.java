package pe.edu.utp.escuela.app.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CrearExamenPeticion;
import pe.edu.utp.escuela.app.dto.CrearOpcionPeticion;
import pe.edu.utp.escuela.app.dto.CrearPreguntaPeticion;
import pe.edu.utp.escuela.app.dto.ExamenRespuesta;
import pe.edu.utp.escuela.app.dto.FinalidadExamen;
import pe.edu.utp.escuela.app.dto.MostrarRespuestas;
import pe.edu.utp.escuela.app.dto.OpcionRespuesta;
import pe.edu.utp.escuela.app.dto.OrdenPeticion;
import pe.edu.utp.escuela.app.dto.PreguntaRespuesta;
import pe.edu.utp.escuela.app.dto.TipoExamen;
import pe.edu.utp.escuela.app.dto.TipoPregunta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.OpcionPregunta;
import pe.edu.utp.escuela.app.entity.Pregunta;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.ExamenRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.OpcionPreguntaRepositorio;
import pe.edu.utp.escuela.app.repository.PreguntaRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-013 — Configurar exámenes: examen, preguntas y opciones. EP02 no crea intentos ni
 * respuestas de alumnos (eso es HU-029/HU-030); tampoco valida que un examen tenga preguntas
 * antes de publicar el curso, porque esa validación de cierre es HU-015. */
@Service
@RequiredArgsConstructor
public class ExamenServicio {

    private final CursoRepositorio cursos;
    private final ModuloRepositorio modulos;
    private final ExamenRepositorio examenes;
    private final PreguntaRepositorio preguntas;
    private final OpcionPreguntaRepositorio opciones;
    private final CurrentUserService currentUserService;
    private final TextNormalizer textos;

    // ---------------------------------------------------------------- Lectura --

    @Transactional(readOnly = true)
    public List<ExamenRespuesta> listarPorCurso(Long cursoId) {
        exigirAdministrador();
        buscarCursoOLanzar(cursoId);
        return construirExamenes(examenes.findByCurso_IdOrderByOrdenAsc(cursoId));
    }

    // ---------------------------------------------------------------- Exámenes --

    @Transactional
    public ExamenRespuesta crearExamen(Long cursoId, CrearExamenPeticion p) {
        exigirAdministrador();
        Curso curso = buscarCursoOLanzar(cursoId);
        Modulo moduloResuelto = resolverModulo(curso, p.finalidad(), p.moduloId());
        String titulo = textos.requireText(p.titulo(), "Título");
        if (examenes.existsByCurso_IdAndTituloIgnoreCase(cursoId, titulo)) {
            throw new DuplicateResourceException("Ya existe un examen con ese título en este curso.");
        }

        Examen examen = new Examen();
        aplicarCamposExamen(examen, curso, moduloResuelto, p, false);
        examen.setOrden((int) examenes.countByCurso_Id(cursoId) + 1);
        examen.setActivo(true);
        examenes.saveAndFlush(examen);
        return mapearExamen(examen, List.of());
    }

    @Transactional
    public ExamenRespuesta actualizarExamen(Long examenId, CrearExamenPeticion p) {
        exigirAdministrador();
        Examen examen = buscarExamenOLanzar(examenId);
        Curso curso = examen.getCurso();
        Modulo moduloResuelto = resolverModulo(curso, p.finalidad(), p.moduloId());
        String titulo = textos.requireText(p.titulo(), "Título");
        if (examenes.existsByCurso_IdAndTituloIgnoreCaseAndIdNot(curso.getId(), titulo, examenId)) {
            throw new DuplicateResourceException("Ya existe un examen con ese título en este curso.");
        }

        aplicarCamposExamen(examen, curso, moduloResuelto, p, true);
        return mapearExamen(examen, preguntas.findByExamen_IdOrderByOrdenAsc(examenId));
    }

    @Transactional
    public ExamenRespuesta cambiarActivoExamen(Long examenId, boolean activo) {
        exigirAdministrador();
        Examen examen = buscarExamenOLanzar(examenId);
        // HU-016: una vez iniciada la convocatoria, un examen CALIFICADO ya no se retira (dejaría
        // sin evaluar a quien ya cursa). Uno PRACTICA sigue pudiendo desactivarse.
        if (!activo && "CALIFICADO".equals(examen.getTipo()) && cursoIniciado(examen.getCurso())) {
            throw new BusinessValidationException(
                    "El curso ya inició: no puedes retirar un examen CALIFICADO de la convocatoria.");
        }
        examen.setActivo(activo);
        return mapearExamen(examen, preguntas.findByExamen_IdOrderByOrdenAsc(examenId));
    }

    @Transactional
    public List<ExamenRespuesta> reordenarExamenes(Long cursoId, OrdenPeticion p) {
        exigirAdministrador();
        buscarCursoOLanzar(cursoId);
        List<Examen> actuales = examenes.findByCurso_IdOrderByOrdenAsc(cursoId);
        Map<Long, Examen> porId = validarOrdenCompleto(actuales, Examen::getId, p.ids(), "exámenes");

        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(-(i + 1));
        }
        examenes.flush();
        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(i + 1);
        }
        examenes.flush();

        return construirExamenes(examenes.findByCurso_IdOrderByOrdenAsc(cursoId));
    }

    /** HU-016 — Al duplicar un curso, los exámenes finales (sin módulo) no viajan con ningún
     * módulo, así que ContenidoServicio.copiarModulo no los alcanza; esta es su copia dedicada.
     * Igual que allí, la fecha de habilitación no se copia (pertenecía al periodo de origen). */
    @Transactional
    public void copiarExamenesFinales(Long cursoDestinoId, Long cursoOrigenId) {
        exigirAdministrador();
        Curso destino = buscarCursoOLanzar(cursoDestinoId);
        List<Examen> finales = examenes.findByCurso_IdOrderByOrdenAsc(cursoOrigenId).stream()
                .filter(Examen::isActivo)
                .filter(e -> FinalidadExamen.FINAL.name().equals(e.getFinalidad()))
                .toList();

        for (Examen origen : finales) {
            Examen copia = new Examen();
            copia.setCurso(destino);
            copia.setModulo(null);
            copia.setExamenOrigenId(origen.getId());
            copia.setTitulo(origen.getTitulo());
            copia.setDescripcion(origen.getDescripcion());
            copia.setTipo(origen.getTipo());
            copia.setFinalidad(origen.getFinalidad());
            copia.setOrden((int) examenes.countByCurso_Id(cursoDestinoId) + 1);
            copia.setMaximoIntentos(origen.getMaximoIntentos());
            copia.setTiempoLimiteMinutos(origen.getTiempoLimiteMinutos());
            copia.setBarajarPreguntas(origen.isBarajarPreguntas());
            copia.setBarajarOpciones(origen.isBarajarOpciones());
            copia.setMostrarRespuestas(origen.getMostrarRespuestas());
            copia.setBloqueaSiguienteModulo(origen.isBloqueaSiguienteModulo());
            copia.setDiasRevision(origen.getDiasRevision());
            copia.setActivo(true);
            examenes.saveAndFlush(copia);

            for (Pregunta preguntaOrigen : preguntas.findByExamen_IdOrderByOrdenAsc(origen.getId())) {
                if (!preguntaOrigen.isActivo()) {
                    continue;
                }
                Pregunta copiaPregunta = new Pregunta();
                copiaPregunta.setExamen(copia);
                copiaPregunta.setTipo(preguntaOrigen.getTipo());
                copiaPregunta.setEnunciado(preguntaOrigen.getEnunciado());
                copiaPregunta.setPuntaje(preguntaOrigen.getPuntaje());
                copiaPregunta.setOrden(preguntaOrigen.getOrden());
                copiaPregunta.setActivo(true);
                preguntas.saveAndFlush(copiaPregunta);

                for (OpcionPregunta opcionOrigen : opciones.findByPregunta_IdOrderByOrdenAsc(preguntaOrigen.getId())) {
                    if (!opcionOrigen.isActivo()) {
                        continue;
                    }
                    OpcionPregunta copiaOpcion = new OpcionPregunta();
                    copiaOpcion.setPregunta(copiaPregunta);
                    copiaOpcion.setTexto(opcionOrigen.getTexto());
                    copiaOpcion.setEsCorrecta(opcionOrigen.isEsCorrecta());
                    copiaOpcion.setOrden(opcionOrigen.getOrden());
                    copiaOpcion.setActivo(true);
                    opciones.saveAndFlush(copiaOpcion);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Preguntas --

    @Transactional
    public PreguntaRespuesta crearPregunta(Long examenId, CrearPreguntaPeticion p) {
        exigirAdministrador();
        Examen examen = buscarExamenOLanzar(examenId);
        List<CrearOpcionPeticion> opcionesValidas = validarOpciones(p.tipo(), p.opciones());

        Pregunta pregunta = new Pregunta();
        pregunta.setExamen(examen);
        pregunta.setTipo(p.tipo().name());
        pregunta.setEnunciado(textos.requireText(p.enunciado(), "Enunciado"));
        pregunta.setPuntaje(p.puntaje());
        pregunta.setOrden((int) preguntas.countByExamen_Id(examenId) + 1);
        pregunta.setActivo(true);
        preguntas.saveAndFlush(pregunta);
        guardarOpciones(pregunta, opcionesValidas);

        return mapearPregunta(pregunta, opciones.findByPregunta_IdOrderByOrdenAsc(pregunta.getId()));
    }

    @Transactional
    public PreguntaRespuesta actualizarPregunta(Long preguntaId, CrearPreguntaPeticion p) {
        exigirAdministrador();
        Pregunta pregunta = buscarPreguntaOLanzar(preguntaId);
        List<CrearOpcionPeticion> opcionesValidas = validarOpciones(p.tipo(), p.opciones());

        pregunta.setTipo(p.tipo().name());
        pregunta.setEnunciado(textos.requireText(p.enunciado(), "Enunciado"));
        pregunta.setPuntaje(p.puntaje());
        opciones.deleteAllByPregunta_Id(preguntaId);
        opciones.flush();
        guardarOpciones(pregunta, opcionesValidas);

        return mapearPregunta(pregunta, opciones.findByPregunta_IdOrderByOrdenAsc(preguntaId));
    }

    @Transactional
    public PreguntaRespuesta cambiarActivoPregunta(Long preguntaId, boolean activo) {
        exigirAdministrador();
        Pregunta pregunta = buscarPreguntaOLanzar(preguntaId);
        pregunta.setActivo(activo);
        return mapearPregunta(pregunta, opciones.findByPregunta_IdOrderByOrdenAsc(preguntaId));
    }

    @Transactional
    public List<PreguntaRespuesta> reordenarPreguntas(Long examenId, OrdenPeticion p) {
        exigirAdministrador();
        buscarExamenOLanzar(examenId);
        List<Pregunta> actuales = preguntas.findByExamen_IdOrderByOrdenAsc(examenId);
        Map<Long, Pregunta> porId = validarOrdenCompleto(actuales, Pregunta::getId, p.ids(), "preguntas");

        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(-(i + 1));
        }
        preguntas.flush();
        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(i + 1);
        }
        preguntas.flush();

        return preguntas.findByExamen_IdOrderByOrdenAsc(examenId).stream()
                .map(preg -> mapearPregunta(preg, opciones.findByPregunta_IdOrderByOrdenAsc(preg.getId())))
                .toList();
    }

    // ---------------------------------------------------------------- Validaciones --

    private Modulo resolverModulo(Curso curso, FinalidadExamen finalidad, Long moduloId) {
        if (finalidad == FinalidadExamen.FINAL) {
            if (moduloId != null) {
                throw new BusinessValidationException("Un examen final no lleva módulo asociado.");
            }
            return null;
        }
        if (moduloId == null) {
            throw new BusinessValidationException("Un examen de módulo requiere elegir el módulo.");
        }
        Modulo modulo = modulos.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("El módulo ya no existe."));
        if (!modulo.getCurso().getId().equals(curso.getId())) {
            throw new BusinessValidationException("El módulo no pertenece a este curso.");
        }
        return modulo;
    }

    private void aplicarCamposExamen(
            Examen examen, Curso curso, Modulo moduloResuelto, CrearExamenPeticion p, boolean esActualizacion) {
        boolean practica = p.tipo() == TipoExamen.PRACTICA;
        boolean esFinal = p.finalidad() == FinalidadExamen.FINAL;

        Integer maximoIntentos = practica ? null : p.maximoIntentos();
        if (p.mostrarRespuestas() == MostrarRespuestas.AL_AGOTAR && maximoIntentos == null) {
            throw new BusinessValidationException(
                    "\"Mostrar respuestas al agotar intentos\" no aplica con intentos ilimitados.");
        }
        if ("VIRTUAL".equals(curso.getModalidad()) && p.fechaHabilitacion() != null) {
            throw new BusinessValidationException(
                    "Un curso virtual no programa fecha de habilitación para exámenes.");
        }

        int diasRevisionSolicitado = p.diasRevision() != null ? p.diasRevision() : 3;
        if (esActualizacion && cursoIniciado(curso) && diasRevisionSolicitado != examen.getDiasRevision()) {
            throw new BusinessValidationException(
                    "El curso ya inició: no puedes cambiar los días de revisión de abiertas.");
        }
        if (esActualizacion && cursoIniciado(curso) && !p.tipo().name().equals(examen.getTipo())) {
            throw new BusinessValidationException(
                    "El curso ya inició: no puedes cambiar el tipo (Calificado/Práctica) de un examen existente.");
        }

        examen.setCurso(curso);
        examen.setModulo(moduloResuelto);
        examen.setTitulo(textos.requireText(p.titulo(), "Título"));
        examen.setDescripcion(textos.trimToNull(p.descripcion()));
        examen.setTipo(p.tipo().name());
        examen.setFinalidad(p.finalidad().name());
        examen.setMaximoIntentos(maximoIntentos);
        examen.setTiempoLimiteMinutos(p.tiempoLimiteMinutos());
        examen.setBarajarPreguntas(p.barajarPreguntas());
        examen.setBarajarOpciones(p.barajarOpciones());
        examen.setMostrarRespuestas(p.mostrarRespuestas().name());
        examen.setFechaHabilitacion("VIRTUAL".equals(curso.getModalidad()) ? null : p.fechaHabilitacion());
        // Un examen de práctica nunca bloquea, y un final no tiene "siguiente módulo" que bloquear.
        examen.setBloqueaSiguienteModulo(!practica && !esFinal && p.bloqueaSiguienteModulo());
        examen.setDiasRevision(diasRevisionSolicitado);
    }

    private boolean cursoIniciado(Curso curso) {
        // Un borrador virtual puede no tener fecha de inicio; eso no lo convierte en un curso
        // iniciado. Las reglas quedan congeladas desde EN_CURSO y siguen congeladas en CERRADO
        // (HU-016: nunca se retrocede).
        if (curso.getEstadoCurso() == null) {
            return false;
        }
        String codigo = curso.getEstadoCurso().getCodigo();
        return "EN_CURSO".equals(codigo) || "CERRADO".equals(codigo);
    }

    private List<CrearOpcionPeticion> validarOpciones(TipoPregunta tipo, List<CrearOpcionPeticion> opcionesPedidas) {
        List<CrearOpcionPeticion> lista = opcionesPedidas == null ? List.of() : opcionesPedidas;
        if (tipo == TipoPregunta.RESPUESTA_ABIERTA) {
            if (!lista.isEmpty()) {
                throw new BusinessValidationException("Una pregunta de respuesta abierta no lleva alternativas.");
            }
            return lista;
        }
        long correctas = lista.stream().filter(CrearOpcionPeticion::esCorrecta).count();
        if (tipo == TipoPregunta.VERDADERO_FALSO) {
            if (lista.size() != 2) {
                throw new BusinessValidationException("Verdadero o falso exige exactamente dos alternativas.");
            }
            if (correctas != 1) {
                throw new BusinessValidationException("Verdadero o falso exige exactamente una alternativa correcta.");
            }
            return lista;
        }
        if (lista.size() < 2) {
            throw new BusinessValidationException("La pregunta exige al menos dos alternativas.");
        }
        if (tipo == TipoPregunta.SELECCION_UNICA && correctas != 1) {
            throw new BusinessValidationException("Selección única exige exactamente una alternativa correcta.");
        }
        if (tipo == TipoPregunta.SELECCION_MULTIPLE && correctas < 1) {
            throw new BusinessValidationException("Selección múltiple exige al menos una alternativa correcta.");
        }
        return lista;
    }

    private void guardarOpciones(Pregunta pregunta, List<CrearOpcionPeticion> lista) {
        int orden = 1;
        for (CrearOpcionPeticion op : lista) {
            OpcionPregunta entidad = new OpcionPregunta();
            entidad.setPregunta(pregunta);
            entidad.setTexto(textos.requireText(op.texto(), "Alternativa"));
            entidad.setEsCorrecta(op.esCorrecta());
            entidad.setOrden(orden++);
            entidad.setActivo(true);
            opciones.save(entidad);
        }
        opciones.flush();
    }

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private Curso buscarCursoOLanzar(Long cursoId) {
        return cursos.findById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso ya no existe."));
    }

    private Examen buscarExamenOLanzar(Long examenId) {
        return examenes.findById(examenId)
                .orElseThrow(() -> new ResourceNotFoundException("El examen ya no existe."));
    }

    private Pregunta buscarPreguntaOLanzar(Long preguntaId) {
        return preguntas.findById(preguntaId)
                .orElseThrow(() -> new ResourceNotFoundException("La pregunta ya no existe."));
    }

    private <T> Map<Long, T> validarOrdenCompleto(
            List<T> actuales, Function<T, Long> idDe, List<Long> idsSolicitados, String etiqueta) {
        Map<Long, T> porId = actuales.stream().collect(Collectors.toMap(idDe, x -> x));
        if (idsSolicitados.size() != porId.size() || !porId.keySet().containsAll(idsSolicitados)
                || idsSolicitados.stream().distinct().count() != idsSolicitados.size()) {
            throw new BusinessValidationException(
                    "La lista de orden debe incluir exactamente todos los " + etiqueta + " actuales, sin repetir.");
        }
        return porId;
    }

    // ---------------------------------------------------------------- Mapeo --

    private List<ExamenRespuesta> construirExamenes(List<Examen> lista) {
        List<Long> examenIds = lista.stream().map(Examen::getId).toList();
        List<Pregunta> todasPreguntas = examenIds.isEmpty() ? List.of() : preguntasDeExamenes(examenIds);
        Map<Long, List<Pregunta>> preguntasPorExamen = todasPreguntas.stream()
                .collect(Collectors.groupingBy(preg -> preg.getExamen().getId()));

        return lista.stream()
                .sorted(Comparator.comparing(Examen::getOrden))
                .map(ex -> mapearExamen(ex, preguntasPorExamen.getOrDefault(ex.getId(), List.of())))
                .toList();
    }

    private List<Pregunta> preguntasDeExamenes(List<Long> examenIds) {
        return examenIds.stream()
                .flatMap(id -> preguntas.findByExamen_IdOrderByOrdenAsc(id).stream())
                .toList();
    }

    private ExamenRespuesta mapearExamen(Examen e, List<Pregunta> preguntasDelExamen) {
        List<PreguntaRespuesta> preguntasRespuesta = preguntasDelExamen.stream()
                .sorted(Comparator.comparing(Pregunta::getOrden))
                .map(preg -> mapearPregunta(preg, opciones.findByPregunta_IdOrderByOrdenAsc(preg.getId())))
                .toList();
        Modulo modulo = e.getModulo();
        return new ExamenRespuesta(
                e.getId(), e.getCurso().getId(), modulo == null ? null : modulo.getId(),
                modulo == null ? null : modulo.getTitulo(), e.getTitulo(), e.getDescripcion(),
                e.getTipo(), e.getFinalidad(), e.getOrden(), e.getMaximoIntentos(), e.getTiempoLimiteMinutos(),
                e.isBarajarPreguntas(), e.isBarajarOpciones(), e.getMostrarRespuestas(), e.getFechaHabilitacion(),
                e.isBloqueaSiguienteModulo(), e.getDiasRevision(), e.isActivo(), e.getExamenOrigenId(),
                preguntasRespuesta);
    }

    private PreguntaRespuesta mapearPregunta(Pregunta p, List<OpcionPregunta> opcionesDePregunta) {
        List<OpcionRespuesta> opcionesRespuesta = opcionesDePregunta.stream()
                .sorted(Comparator.comparing(OpcionPregunta::getOrden))
                .map(op -> new OpcionRespuesta(op.getId(), op.getTexto(), op.isEsCorrecta(), op.getOrden()))
                .toList();
        return new PreguntaRespuesta(
                p.getId(), p.getTipo(), p.getEnunciado(), p.getPuntaje(), p.getOrden(), p.isActivo(),
                opcionesRespuesta);
    }
}
