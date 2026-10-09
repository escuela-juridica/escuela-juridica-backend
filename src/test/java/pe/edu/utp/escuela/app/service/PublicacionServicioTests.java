package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.dto.ErrorValidacionCurso;
import pe.edu.utp.escuela.app.dto.ValidacionPublicacionRespuesta;
import pe.edu.utp.escuela.app.entity.CategoriaTematica;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.CursoDocente;
import pe.edu.utp.escuela.app.entity.CursoFirmante;
import pe.edu.utp.escuela.app.entity.EntidadCertificadora;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Firmante;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Recurso;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.TipoCurso;
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
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;

/** HU-015 — Validar y publicar un curso. La convocatoria de referencia es VIRTUAL y gratuita
 * (el camino más corto sin sesiones en vivo ni pagos), con exactamente lo necesario para que
 * {@code evaluar()} no reporte hallazgos; cada prueba negativa rompe un único requisito sobre esa
 * misma base. */
@ExtendWith(MockitoExtension.class)
class PublicacionServicioTests {

    @Mock private CursoRepositorio cursos;
    @Mock private ModuloRepositorio modulos;
    @Mock private LeccionRepositorio lecciones;
    @Mock private MaterialLeccionRepositorio materiales;
    @Mock private ExamenRepositorio examenes;
    @Mock private PreguntaRepositorio preguntas;
    @Mock private ReglaCursoRepositorio reglasCurso;
    @Mock private CursoFirmanteRepositorio cursoFirmantes;
    @Mock private CursoDocenteRepositorio cursoDocentes;
    @Mock private EstadoCursoRepositorio estadosCurso;
    @Mock private HistorialEstadoCursoRepositorio historial;
    @Mock private CurrentUserService currentUserService;

    private PublicacionServicio servicio;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("America/Lima"));
        servicio = new PublicacionServicio(cursos, modulos, lecciones, materiales, examenes, preguntas,
                reglasCurso, cursoFirmantes, cursoDocentes, estadosCurso, historial, currentUserService, clock);
        when(currentUserService.get())
                .thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    /** Arma un BORRADOR virtual y gratuito que cumple cada regla de {@code evaluar()}, y deja
     * sus piezas (módulo, lección, regla, firmante, docente) listas para que una prueba negativa
     * rompa una sola de ellas antes de llamar a {@link #stub(Curso, Modulo, Leccion, ReglaCurso,
     * CursoFirmante, CursoDocente)}. */
    private Curso cursoValidoBase() {
        EstadoCurso borrador = new EstadoCurso();
        borrador.setCodigo("BORRADOR");

        TipoCurso tipoCurso = new TipoCurso();
        tipoCurso.setId(1L);
        tipoCurso.setActivo(true);

        CategoriaTematica categoria = new CategoriaTematica();
        categoria.setId(1L);
        categoria.setActivo(true);

        EntidadCertificadora entidad = new EntidadCertificadora();
        entidad.setId(1L);
        entidad.setActivo(true);

        Curso curso = new Curso();
        curso.setId(100L);
        curso.setTitulo("Diplomado de prueba");
        curso.setDescripcion("Descripción completa del curso.");
        curso.setTipoCurso(tipoCurso);
        curso.setCategoriaTematica(categoria);
        curso.setEntidadCertificadora(entidad);
        curso.setHorasAcademicas(BigDecimal.TEN);
        curso.setModalidad("VIRTUAL");
        curso.setFechaFin(null);
        curso.setTipoVenta("GRATUITO");
        curso.setPrecioRegular(BigDecimal.ZERO);
        curso.setEstadoCurso(borrador);
        return curso;
    }

    private Modulo moduloActivoDe(Curso curso) {
        Modulo modulo = new Modulo();
        modulo.setId(10L);
        modulo.setCurso(curso);
        modulo.setActivo(true);
        return modulo;
    }

    private Leccion leccionCursableDe(Modulo modulo) {
        Leccion leccion = new Leccion();
        leccion.setId(20L);
        leccion.setModulo(modulo);
        leccion.setTitulo("Lección 1");
        leccion.setTipo("GRABADA");
        leccion.setEsObligatoria(true);
        leccion.setEsVistaPrevia(false);
        leccion.setActivo(true);
        return leccion;
    }

    private MaterialLeccion materialConContenidoDe(Leccion leccion) {
        Recurso recurso = new Recurso();
        recurso.setId(30L);
        recurso.setActivo(true);
        MaterialLeccion material = new MaterialLeccion();
        material.setId(40L);
        material.setLeccion(leccion);
        material.setRecurso(recurso);
        material.setActivo(true);
        return material;
    }

    private ReglaCurso reglaValidaDe(Curso curso) {
        ReglaCurso regla = new ReglaCurso();
        regla.setCurso(curso);
        regla.setRequiereExamenes(false);
        regla.setRequiereProgreso(true);
        regla.setRequiereAsistencia(false);
        regla.setNotaMinima(BigDecimal.valueOf(12));
        regla.setNotaRefrendado(BigDecimal.valueOf(14));
        regla.setProgresoMinimo(BigDecimal.valueOf(80));
        regla.setUmbralVideo(BigDecimal.valueOf(50));
        regla.setAsistenciaMinima(BigDecimal.valueOf(80));
        regla.setSecuenciaObligatoria(true);
        regla.setDiasEsperaCertificado(0);
        return regla;
    }

    private CursoFirmante firmanteValidoDe(Curso curso) {
        Persona persona = new Persona();
        persona.setNombres("Ana");
        persona.setApellidoPaterno("Gómez");
        persona.setActivo(true);
        Firmante firmante = new Firmante();
        firmante.setId(50L);
        firmante.setPersona(persona);
        firmante.setActivo(true);
        firmante.setImagenFirmaUrl("https://cdn.example.com/firma.png");
        CursoFirmante cf = new CursoFirmante();
        cf.setCurso(curso);
        cf.setFirmante(firmante);
        cf.setOrden(1);
        return cf;
    }

    private CursoDocente docenteValidoDe(Curso curso) {
        Persona persona = new Persona();
        persona.setId(60L);
        persona.setNombres("Carlos");
        persona.setApellidoPaterno("Ruiz");
        persona.setActivo(true);
        CursoDocente cd = new CursoDocente();
        cd.setCurso(curso);
        cd.setPersona(persona);
        cd.setOrden(1);
        return cd;
    }

    /** Conecta los mocks para que {@code evaluar()} recorra exactamente las piezas dadas. */
    private void stub(Curso curso, Modulo modulo, Leccion leccion, ReglaCurso regla,
            CursoFirmante firmante, CursoDocente docente) {
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(100L))
                .thenReturn(modulo == null ? List.of() : List.of(modulo));
        List<Leccion> leccionesActivas = leccion == null ? List.of() : List.of(leccion);
        when(lecciones.buscarActivasDeModulos(List.of(10L))).thenReturn(leccionesActivas);
        List<Long> leccionIds = leccion == null ? List.of() : List.of(leccion.getId());
        MaterialLeccion material = leccion == null ? null : materialConContenidoDe(leccion);
        // Cuando no hay lección (leccionIds vacío), evaluar() nunca llega a invocar estos dos
        // métodos: quedan en lenient() para no fallar por "stubbing innecesario" en ese caso.
        org.mockito.Mockito.lenient().when(materiales.findByLeccion_IdInOrderByLeccion_IdAscOrdenAsc(leccionIds))
                .thenReturn(material == null ? List.of() : List.of(material));
        org.mockito.Mockito.lenient().when(materiales.buscarDuraciones(leccionIds)).thenReturn(List.of());
        when(examenes.findByCurso_IdOrderByOrdenAsc(100L)).thenReturn(List.of());
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.ofNullable(regla));
        when(cursoFirmantes.findByCurso_IdOrderByOrdenAsc(100L))
                .thenReturn(firmante == null ? List.of() : List.of(firmante));
        when(cursoDocentes.findByCurso_IdOrderByOrdenAsc(100L))
                .thenReturn(docente == null ? List.of() : List.of(docente));
    }

    @Test
    void validarCursoCompletoNoMuestraBloqueos() {
        // HU-015 Escenario 1 (validación completa).
        Curso curso = cursoValidoBase();
        Modulo modulo = moduloActivoDe(curso);
        Leccion leccion = leccionCursableDe(modulo);
        stub(curso, modulo, leccion, reglaValidaDe(curso), firmanteValidoDe(curso), docenteValidoDe(curso));

        ValidacionPublicacionRespuesta respuesta = servicio.validar(100L);

        assertTrue(respuesta.puedePublicarse());
        assertTrue(respuesta.hallazgos().isEmpty());
        assertFalse(respuesta.publicacionRealizada());
    }

    @Test
    void publicarCursoVirtualSinFechaInicioQuedaDirectamenteEnCurso() {
        // HU-015 Escenario 3 (publicación): virtual sin fecha de inicio pasa directo a EN_CURSO.
        Curso curso = cursoValidoBase();
        Modulo modulo = moduloActivoDe(curso);
        Leccion leccion = leccionCursableDe(modulo);
        stub(curso, modulo, leccion, reglaValidaDe(curso), firmanteValidoDe(curso), docenteValidoDe(curso));
        EstadoCurso enCurso = new EstadoCurso();
        enCurso.setCodigo("EN_CURSO");
        when(estadosCurso.findByCodigo("EN_CURSO")).thenReturn(Optional.of(enCurso));

        ValidacionPublicacionRespuesta respuesta = servicio.publicar(100L);

        assertTrue(respuesta.puedePublicarse());
        assertTrue(respuesta.publicacionRealizada());
        assertEquals("EN_CURSO", respuesta.estadoCodigo());
        assertEquals("EN_CURSO", curso.getEstadoCurso().getCodigo());
        org.mockito.Mockito.verify(historial).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publicarConTituloVacioNoCambiaElEstado() {
        // HU-015 Escenario 2 (bloqueo de publicación).
        Curso curso = cursoValidoBase();
        curso.setTitulo("   ");
        Modulo modulo = moduloActivoDe(curso);
        Leccion leccion = leccionCursableDe(modulo);
        stub(curso, modulo, leccion, reglaValidaDe(curso), firmanteValidoDe(curso), docenteValidoDe(curso));

        ValidacionPublicacionRespuesta respuesta = servicio.publicar(100L);

        assertFalse(respuesta.puedePublicarse());
        assertFalse(respuesta.publicacionRealizada());
        assertEquals("BORRADOR", curso.getEstadoCurso().getCodigo());
        assertTrue(respuesta.hallazgos().stream().anyMatch(h -> "titulo".equals(h.campo())));
    }

    @Test
    void publicarSinDocentesNoCambiaElEstado() {
        Curso curso = cursoValidoBase();
        Modulo modulo = moduloActivoDe(curso);
        Leccion leccion = leccionCursableDe(modulo);
        stub(curso, modulo, leccion, reglaValidaDe(curso), firmanteValidoDe(curso), null);

        ValidacionPublicacionRespuesta respuesta = servicio.publicar(100L);

        assertFalse(respuesta.puedePublicarse());
        assertTrue(respuesta.hallazgos().stream().anyMatch(h -> "DOCENTES".equals(h.seccion())));
    }

    @Test
    void publicarSinFirmantesNoCambiaElEstado() {
        Curso curso = cursoValidoBase();
        Modulo modulo = moduloActivoDe(curso);
        Leccion leccion = leccionCursableDe(modulo);
        stub(curso, modulo, leccion, reglaValidaDe(curso), null, docenteValidoDe(curso));

        ValidacionPublicacionRespuesta respuesta = servicio.publicar(100L);

        assertFalse(respuesta.puedePublicarse());
        assertTrue(respuesta.hallazgos().stream().anyMatch(h -> "CERTIFICADO".equals(h.seccion())));
    }

    @Test
    void publicarSinContenidoCursableNoCambiaElEstado() {
        Curso curso = cursoValidoBase();
        Modulo modulo = moduloActivoDe(curso);
        stub(curso, modulo, null, reglaValidaDe(curso), firmanteValidoDe(curso), docenteValidoDe(curso));

        ValidacionPublicacionRespuesta respuesta = servicio.publicar(100L);

        assertFalse(respuesta.puedePublicarse());
        assertTrue(respuesta.hallazgos().stream()
                .anyMatch(h -> "CONTENIDO".equals(h.seccion()) && "modulos".equals(h.campo())));
    }

    @Test
    void publicarSinExamenCalificadoCuandoSeExigenExamenesNoCambiaElEstado() {
        Curso curso = cursoValidoBase();
        Modulo modulo = moduloActivoDe(curso);
        Leccion leccion = leccionCursableDe(modulo);
        ReglaCurso regla = reglaValidaDe(curso);
        regla.setRequiereExamenes(true);
        stub(curso, modulo, leccion, regla, firmanteValidoDe(curso), docenteValidoDe(curso));

        ValidacionPublicacionRespuesta respuesta = servicio.publicar(100L);

        assertFalse(respuesta.puedePublicarse());
        assertTrue(respuesta.hallazgos().stream()
                .anyMatch(h -> "EXAMENES".equals(h.seccion()) && "calificados".equals(h.campo())));
    }

    @Test
    void validarUnCursoQueYaNoEstaEnBorradorNoEvaluaNada() {
        Curso curso = cursoValidoBase();
        curso.getEstadoCurso().setCodigo("PUBLICADO");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        ValidacionPublicacionRespuesta respuesta = servicio.validar(100L);

        assertFalse(respuesta.puedePublicarse());
        assertTrue(respuesta.hallazgos().isEmpty());
        assertEquals("PUBLICADO", respuesta.estadoCodigo());
    }
}
