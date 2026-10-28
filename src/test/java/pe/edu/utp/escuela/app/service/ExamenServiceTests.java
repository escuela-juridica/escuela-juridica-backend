package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.dto.CrearExamenRequest;
import pe.edu.utp.escuela.app.dto.CrearOpcionRequest;
import pe.edu.utp.escuela.app.dto.CrearPreguntaRequest;
import pe.edu.utp.escuela.app.dto.ExamenResponse;
import pe.edu.utp.escuela.app.dto.FinalidadExamen;
import pe.edu.utp.escuela.app.dto.MostrarRespuestas;
import pe.edu.utp.escuela.app.dto.PreguntaResponse;
import pe.edu.utp.escuela.app.dto.TipoExamen;
import pe.edu.utp.escuela.app.dto.TipoPregunta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.ExamenRepository;
import pe.edu.utp.escuela.app.repository.ModuloRepository;
import pe.edu.utp.escuela.app.repository.OpcionPreguntaRepository;
import pe.edu.utp.escuela.app.repository.PreguntaRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-013 — Configurar exámenes, preguntas y alternativas. */
@ExtendWith(MockitoExtension.class)
class ExamenServiceTests {

    @Mock private CursoRepository cursos;
    @Mock private ModuloRepository modulos;
    @Mock private ExamenRepository examenes;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionPreguntaRepository opciones;
    @Mock private CurrentUserService currentUserService;

    private ExamenService service;

    @BeforeEach
    void setUp() {
        service = new ExamenService(cursos, modulos, examenes, preguntas, opciones, currentUserService,
                new TextNormalizer());
        when(currentUserService.get())
                .thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    private Curso cursoEnEstado(String codigo) {
        EstadoCurso estado = new EstadoCurso();
        estado.setCodigo(codigo);
        Curso curso = new Curso();
        curso.setId(100L);
        curso.setModalidad("EN_VIVO");
        curso.setEstadoCurso(estado);
        return curso;
    }

    private Modulo moduloDe(Curso curso) {
        Modulo modulo = new Modulo();
        modulo.setId(10L);
        modulo.setCurso(curso);
        modulo.setTitulo("Módulo 1");
        return modulo;
    }

    private CrearExamenRequest peticionModulo(Long moduloId) {
        return new CrearExamenRequest(moduloId, "Examen del módulo", null, TipoExamen.CALIFICADO,
                FinalidadExamen.MODULO, 2, 60, false, false, MostrarRespuestas.AL_APROBAR, null, true, 3);
    }

    private CrearExamenRequest peticionFinal() {
        return new CrearExamenRequest(null, "Examen final", null, TipoExamen.CALIFICADO,
                FinalidadExamen.FINAL, 1, 90, false, false, MostrarRespuestas.AL_APROBAR, null, true, 3);
    }

    // ---------------------------------------------------------------- Exámenes --

    @Test
    void crearExamenDeModuloValidoQuedaEnEstadoEditable() {
        // HU-013 Escenario 1 (configuración del examen).
        Curso curso = cursoEnEstado("BORRADOR");
        Modulo modulo = moduloDe(curso);
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));
        when(examenes.existsByCurso_IdAndTituloIgnoreCase(100L, "Examen del módulo")).thenReturn(false);
        when(examenes.countByCurso_Id(100L)).thenReturn(0L);

        ExamenResponse respuesta = service.crearExamen(100L, peticionModulo(10L));

        assertEquals("Examen del módulo", respuesta.titulo());
        assertTrue(respuesta.activo());
        assertEquals(10L, respuesta.moduloId());
    }

    @Test
    void crearExamenFinalConModuloIdLanzaValidacion() {
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));

        CrearExamenRequest peticion = new CrearExamenRequest(10L, "Examen final", null, TipoExamen.CALIFICADO,
                FinalidadExamen.FINAL, 1, 90, false, false, MostrarRespuestas.AL_APROBAR, null, true, 3);

        assertThrows(BusinessValidationException.class, () -> service.crearExamen(100L, peticion));
    }

    @Test
    void crearExamenDeModuloSinModuloIdLanzaValidacion() {
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class, () -> service.crearExamen(100L, peticionModulo(null)));
    }

    @Test
    void crearExamenConModuloDeOtroCursoLanzaValidacion() {
        Curso curso = cursoEnEstado("BORRADOR");
        Curso otroCurso = cursoEnEstado("BORRADOR");
        otroCurso.setId(200L);
        Modulo moduloAjeno = moduloDe(otroCurso);
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        when(modulos.findById(10L)).thenReturn(Optional.of(moduloAjeno));

        assertThrows(BusinessValidationException.class, () -> service.crearExamen(100L, peticionModulo(10L)));
    }

    @Test
    void crearExamenConTituloDuplicadoEnElMismoCursoLanzaDuplicado() {
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        when(examenes.existsByCurso_IdAndTituloIgnoreCase(100L, "Examen final")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.crearExamen(100L, peticionFinal()));
    }

    @Test
    void crearExamenConFechaHabilitacionEnCursoVirtualLanzaValidacion() {
        Curso curso = cursoEnEstado("BORRADOR");
        curso.setModalidad("VIRTUAL");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        when(examenes.existsByCurso_IdAndTituloIgnoreCase(100L, "Examen final")).thenReturn(false);

        CrearExamenRequest peticion = new CrearExamenRequest(null, "Examen final", null, TipoExamen.CALIFICADO,
                FinalidadExamen.FINAL, 1, 90, false, false, MostrarRespuestas.AL_APROBAR,
                java.time.Instant.parse("2026-09-20T10:00:00Z"), true, 3);

        assertThrows(BusinessValidationException.class, () -> service.crearExamen(100L, peticion));
    }

    @Test
    void mostrarRespuestasAlAgotarConIntentosIlimitadosLanzaValidacion() {
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        when(examenes.existsByCurso_IdAndTituloIgnoreCase(100L, "Examen práctica")).thenReturn(false);

        // PRACTICA siempre tiene maximoIntentos=null, así que AL_AGOTAR nunca aplica aquí.
        CrearExamenRequest peticion = new CrearExamenRequest(null, "Examen práctica", null, TipoExamen.PRACTICA,
                FinalidadExamen.FINAL, 5, 90, false, false, MostrarRespuestas.AL_AGOTAR, null, true, 3);

        assertThrows(BusinessValidationException.class, () -> service.crearExamen(100L, peticion));
    }

    @Test
    void examenPracticaNuncaBloqueaElSiguienteModuloAunqueSeSolicite() {
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        when(examenes.existsByCurso_IdAndTituloIgnoreCase(100L, "Práctica")).thenReturn(false);
        when(examenes.countByCurso_Id(100L)).thenReturn(0L);

        CrearExamenRequest peticion = new CrearExamenRequest(null, "Práctica", null, TipoExamen.PRACTICA,
                FinalidadExamen.FINAL, null, null, false, false, MostrarRespuestas.AL_APROBAR, null, true, 3);

        ExamenResponse respuesta = service.crearExamen(100L, peticion);

        assertFalse(respuesta.bloqueaSiguienteModulo());
    }

    @Test
    void actualizarExamenConCursoIniciadoCambiandoDiasDeRevisionLanzaValidacion() {
        // HU-013 Escenario 3 (restricción temporal).
        Curso curso = cursoEnEstado("EN_CURSO");
        Examen examen = new Examen();
        examen.setId(50L);
        examen.setCurso(curso);
        examen.setTipo(TipoExamen.CALIFICADO.name());
        examen.setDiasRevision(3);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(examenes.existsByCurso_IdAndTituloIgnoreCaseAndIdNot(100L, "Examen final", 50L)).thenReturn(false);

        CrearExamenRequest peticion = new CrearExamenRequest(null, "Examen final", null, TipoExamen.CALIFICADO,
                FinalidadExamen.FINAL, 1, 90, false, false, MostrarRespuestas.AL_APROBAR, null, true, 7);

        assertThrows(BusinessValidationException.class, () -> service.actualizarExamen(50L, peticion));
    }

    @Test
    void actualizarExamenConCursoIniciadoCambiandoTipoLanzaValidacion() {
        Curso curso = cursoEnEstado("EN_CURSO");
        Examen examen = new Examen();
        examen.setId(50L);
        examen.setCurso(curso);
        examen.setTipo(TipoExamen.CALIFICADO.name());
        examen.setDiasRevision(3);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(examenes.existsByCurso_IdAndTituloIgnoreCaseAndIdNot(100L, "Examen final", 50L)).thenReturn(false);

        CrearExamenRequest peticion = new CrearExamenRequest(null, "Examen final", null, TipoExamen.PRACTICA,
                FinalidadExamen.FINAL, null, 90, false, false, MostrarRespuestas.AL_APROBAR, null, true, 3);

        assertThrows(BusinessValidationException.class, () -> service.actualizarExamen(50L, peticion));
    }

    @Test
    void actualizarExamenConCursoIniciadoSinCambiarLaReglaBloqueadaFunciona() {
        Curso curso = cursoEnEstado("EN_CURSO");
        Examen examen = new Examen();
        examen.setId(50L);
        examen.setCurso(curso);
        examen.setTipo(TipoExamen.CALIFICADO.name());
        examen.setDiasRevision(3);
        examen.setOrden(1);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(examenes.existsByCurso_IdAndTituloIgnoreCaseAndIdNot(100L, "Examen final", 50L)).thenReturn(false);
        when(preguntas.findByExamen_IdOrderByOrdenAsc(50L)).thenReturn(List.of());

        CrearExamenRequest peticion = new CrearExamenRequest(null, "Examen final", null, TipoExamen.CALIFICADO,
                FinalidadExamen.FINAL, 2, 90, false, false, MostrarRespuestas.AL_APROBAR, null, true, 3);

        ExamenResponse respuesta = service.actualizarExamen(50L, peticion);

        assertEquals("Examen final", respuesta.titulo());
    }

    @Test
    void cambiarActivoExamenCalificadoConCursoIniciadoLanzaValidacion() {
        Curso curso = cursoEnEstado("EN_CURSO");
        Examen examen = new Examen();
        examen.setId(50L);
        examen.setCurso(curso);
        examen.setTipo(TipoExamen.CALIFICADO.name());
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));

        assertThrows(BusinessValidationException.class, () -> service.cambiarActivoExamen(50L, false));
    }

    @Test
    void cambiarActivoExamenPracticaConCursoIniciadoSiEstaPermitido() {
        Curso curso = cursoEnEstado("EN_CURSO");
        Examen examen = new Examen();
        examen.setId(50L);
        examen.setCurso(curso);
        examen.setTipo(TipoExamen.PRACTICA.name());
        examen.setOrden(1);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(preguntas.findByExamen_IdOrderByOrdenAsc(50L)).thenReturn(List.of());

        ExamenResponse respuesta = service.cambiarActivoExamen(50L, false);

        assertFalse(respuesta.activo());
    }

    // ---------------------------------------------------------------- Preguntas --

    @Test
    void crearPreguntaSeleccionUnicaConUnaCorrectaFunciona() {
        // HU-013 Escenario 2 (banco de preguntas).
        Examen examen = new Examen();
        examen.setId(50L);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(preguntas.countByExamen_Id(50L)).thenReturn(0L);
        when(opciones.findByPregunta_IdOrderByOrdenAsc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        CrearPreguntaRequest peticion = new CrearPreguntaRequest(TipoPregunta.SELECCION_UNICA, "¿2+2?",
                BigDecimal.valueOf(2), List.of(
                        new CrearOpcionRequest("3", false),
                        new CrearOpcionRequest("4", true)));

        PreguntaResponse respuesta = service.crearPregunta(50L, peticion);

        assertEquals("¿2+2?", respuesta.enunciado());
    }

    @Test
    void crearPreguntaSeleccionUnicaConDosCorrectasLanzaValidacion() {
        Examen examen = new Examen();
        examen.setId(50L);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));

        CrearPreguntaRequest peticion = new CrearPreguntaRequest(TipoPregunta.SELECCION_UNICA, "¿2+2?",
                BigDecimal.valueOf(2), List.of(
                        new CrearOpcionRequest("4", true),
                        new CrearOpcionRequest("4 también", true)));

        assertThrows(BusinessValidationException.class, () -> service.crearPregunta(50L, peticion));
    }

    @Test
    void crearPreguntaSeleccionMultipleSinAlternativaCorrectaLanzaValidacion() {
        Examen examen = new Examen();
        examen.setId(50L);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));

        CrearPreguntaRequest peticion = new CrearPreguntaRequest(TipoPregunta.SELECCION_MULTIPLE, "Marca pares",
                BigDecimal.valueOf(3), List.of(
                        new CrearOpcionRequest("1", false),
                        new CrearOpcionRequest("2", false)));

        assertThrows(BusinessValidationException.class, () -> service.crearPregunta(50L, peticion));
    }

    @Test
    void crearPreguntaVerdaderoFalsoConDosAlternativasYUnaCorrectaFunciona() {
        Examen examen = new Examen();
        examen.setId(50L);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(preguntas.countByExamen_Id(50L)).thenReturn(0L);
        when(opciones.findByPregunta_IdOrderByOrdenAsc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        CrearPreguntaRequest peticion = new CrearPreguntaRequest(TipoPregunta.VERDADERO_FALSO, "¿Es correcto?",
                BigDecimal.ONE, List.of(
                        new CrearOpcionRequest("Verdadero", true),
                        new CrearOpcionRequest("Falso", false)));

        PreguntaResponse respuesta = service.crearPregunta(50L, peticion);

        assertEquals("VERDADERO_FALSO", respuesta.tipo());
    }

    @Test
    void crearPreguntaVerdaderoFalsoConMasDeDosAlternativasLanzaValidacion() {
        Examen examen = new Examen();
        examen.setId(50L);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));

        CrearPreguntaRequest peticion = new CrearPreguntaRequest(TipoPregunta.VERDADERO_FALSO, "¿Es correcto?",
                BigDecimal.ONE, List.of(
                        new CrearOpcionRequest("Verdadero", true),
                        new CrearOpcionRequest("Falso", false),
                        new CrearOpcionRequest("Tal vez", false)));

        assertThrows(BusinessValidationException.class, () -> service.crearPregunta(50L, peticion));
    }

    @Test
    void crearPreguntaRespuestaAbiertaConAlternativasLanzaValidacion() {
        Examen examen = new Examen();
        examen.setId(50L);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));

        CrearPreguntaRequest peticion = new CrearPreguntaRequest(TipoPregunta.RESPUESTA_ABIERTA,
                "Explica brevemente", BigDecimal.valueOf(5), List.of(new CrearOpcionRequest("No debería ir", false)));

        assertThrows(BusinessValidationException.class, () -> service.crearPregunta(50L, peticion));
    }

    @Test
    void crearPreguntaRespuestaAbiertaSinAlternativasFunciona() {
        Examen examen = new Examen();
        examen.setId(50L);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(preguntas.countByExamen_Id(50L)).thenReturn(0L);
        when(opciones.findByPregunta_IdOrderByOrdenAsc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        CrearPreguntaRequest peticion = new CrearPreguntaRequest(TipoPregunta.RESPUESTA_ABIERTA,
                "Explica brevemente", BigDecimal.valueOf(5), List.of());

        PreguntaResponse respuesta = service.crearPregunta(50L, peticion);

        assertTrue(respuesta.opciones().isEmpty());
    }

    // ---------------------------------------------------------------- Eliminar (HU-016 apoyo) --

    @Test
    void eliminarExamenConCursoEnBorradorLoElimina() {
        Curso curso = cursoEnEstado("BORRADOR");
        Examen examen = new Examen();
        examen.setId(50L);
        examen.setCurso(curso);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));
        when(preguntas.findByExamen_IdOrderByOrdenAsc(50L)).thenReturn(List.of());

        service.eliminarExamen(50L);

        org.mockito.Mockito.verify(examenes).delete(examen);
    }

    @Test
    void eliminarExamenConCursoYaPublicadoLanzaValidacion() {
        Curso curso = cursoEnEstado("PUBLICADO");
        Examen examen = new Examen();
        examen.setId(50L);
        examen.setCurso(curso);
        when(examenes.findById(50L)).thenReturn(Optional.of(examen));

        assertThrows(BusinessValidationException.class, () -> service.eliminarExamen(50L));
    }
}
