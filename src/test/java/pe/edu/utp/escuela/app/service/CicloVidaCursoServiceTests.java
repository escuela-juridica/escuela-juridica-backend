package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.dto.CerrarCursoRequest;
import pe.edu.utp.escuela.app.dto.RetrasarInicioRequest;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.repository.CursoDocenteRepository;
import pe.edu.utp.escuela.app.repository.CursoFirmanteRepository;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.EstadoCursoRepository;
import pe.edu.utp.escuela.app.repository.ExamenRepository;
import pe.edu.utp.escuela.app.repository.HistorialEstadoCursoRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.ModuloRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-016 — Administrar el ciclo de vida del curso. */
@ExtendWith(MockitoExtension.class)
class CicloVidaCursoServiceTests {

    @Mock private CursoRepository cursos;
    @Mock private EstadoCursoRepository estadosCurso;
    @Mock private HistorialEstadoCursoRepository historial;
    @Mock private ReglaCursoRepository reglasCurso;
    @Mock private CursoDocenteRepository cursoDocentes;
    @Mock private CursoFirmanteRepository cursoFirmantes;
    @Mock private ModuloRepository modulos;
    @Mock private ExamenRepository examenes;
    @Mock private MatriculaRepository matriculas;
    @Mock private CurrentUserService currentUserService;
    @Mock private CursoService cursoService;
    @Mock private ContenidoService contenidoService;
    @Mock private ExamenService examenService;

    private CicloVidaCursoService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("America/Lima"));
        service = new CicloVidaCursoService(cursos, estadosCurso, historial, reglasCurso, cursoDocentes,
                cursoFirmantes, modulos, examenes, matriculas, currentUserService, new TextNormalizer(), clock,
                cursoService, contenidoService, examenService);
        // lenient(): aplicarTransicionesAutomaticas() es un job sin usuario autenticado y nunca
        // llama a currentUserService, así que este stub sería "innecesario" solo en esas pruebas.
        org.mockito.Mockito.lenient().when(currentUserService.get())
                .thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    private Curso cursoEnEstado(String codigo) {
        EstadoCurso estado = new EstadoCurso();
        estado.setCodigo(codigo);
        Curso curso = new Curso();
        curso.setId(100L);
        curso.setEstadoCurso(estado);
        return curso;
    }

    // ---------------------------------------------------------------- Adelantar inicio --

    @Test
    void adelantarInicioConCursoPublicadoPasaAEnCurso() {
        // HU-016 Escenario 1 (transición permitida).
        Curso curso = cursoEnEstado("PUBLICADO");
        curso.setFechaInicio(LocalDate.of(2026, 10, 1));
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        EstadoCurso enCurso = new EstadoCurso();
        enCurso.setCodigo("EN_CURSO");
        when(estadosCurso.findByCodigo("EN_CURSO")).thenReturn(Optional.of(enCurso));

        service.adelantarInicio(100L);

        assertEquals("EN_CURSO", curso.getEstadoCurso().getCodigo());
        assertEquals(LocalDate.of(2026, 9, 15), curso.getFechaInicio());
        verify(historial).saveAndFlush(any());
    }

    @Test
    void adelantarInicioConCursoNoPublicadoLanzaValidacion() {
        // HU-016 Escenario 2 (transición inválida).
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class, () -> service.adelantarInicio(100L));
        assertEquals("BORRADOR", curso.getEstadoCurso().getCodigo());
    }

    // ---------------------------------------------------------------- Retrasar inicio --

    @Test
    void retrasarInicioConFechaPosteriorLaConserva() {
        Curso curso = cursoEnEstado("PUBLICADO");
        curso.setFechaInicio(LocalDate.of(2026, 10, 1));
        curso.setFechaFin(LocalDate.of(2026, 12, 1));
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        service.retrasarInicio(100L, new RetrasarInicioRequest(LocalDate.of(2026, 10, 15)));

        assertEquals(LocalDate.of(2026, 10, 15), curso.getFechaInicio());
        verify(historial, never()).saveAndFlush(any());
    }

    @Test
    void retrasarInicioConFechaNoPosteriorALaActualLanzaValidacion() {
        Curso curso = cursoEnEstado("PUBLICADO");
        curso.setFechaInicio(LocalDate.of(2026, 10, 1));
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class, () -> service.retrasarInicio(100L,
                new RetrasarInicioRequest(LocalDate.of(2026, 9, 20))));
    }

    @Test
    void retrasarInicioMasAlladeLaFechaFinLanzaValidacion() {
        Curso curso = cursoEnEstado("PUBLICADO");
        curso.setFechaInicio(LocalDate.of(2026, 10, 1));
        curso.setFechaFin(LocalDate.of(2026, 10, 10));
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class, () -> service.retrasarInicio(100L,
                new RetrasarInicioRequest(LocalDate.of(2026, 11, 1))));
    }

    @Test
    void retrasarInicioConCursoNoPublicadoLanzaValidacion() {
        Curso curso = cursoEnEstado("EN_CURSO");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class, () -> service.retrasarInicio(100L,
                new RetrasarInicioRequest(LocalDate.of(2026, 11, 1))));
    }

    // ---------------------------------------------------------------- Cerrar --

    @Test
    void cerrarUnCursoPublicadoOEnCursoLoCierra() {
        Curso curso = cursoEnEstado("EN_CURSO");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        EstadoCurso cerrado = new EstadoCurso();
        cerrado.setCodigo("CERRADO");
        when(estadosCurso.findByCodigo("CERRADO")).thenReturn(Optional.of(cerrado));

        service.cerrar(100L, new CerrarCursoRequest("Cierre por baja demanda"));

        assertEquals("CERRADO", curso.getEstadoCurso().getCodigo());
    }

    @Test
    void cerrarUnCursoEnBorradorLanzaValidacion() {
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class,
                () -> service.cerrar(100L, new CerrarCursoRequest(null)));
    }

    // ---------------------------------------------------------------- Eliminar --

    @Test
    void eliminarCursoEnBorradorSinMatriculasLoElimina() {
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(matriculas.existsByCurso_Id(100L)).thenReturn(false);
        when(modulos.findByCurso_IdOrderByOrdenAsc(100L)).thenReturn(List.of());
        when(examenes.findByCurso_IdOrderByOrdenAsc(100L)).thenReturn(List.of());

        service.eliminarCurso(100L);

        verify(cursos).delete(curso);
        verify(historial).deleteAllByCurso_Id(100L);
    }

    @Test
    void eliminarCursoConMatriculasLanzaValidacion() {
        // HU-016 Escenario 3 (conservación): no se elimina un curso que ya tiene alumnos.
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(matriculas.existsByCurso_Id(100L)).thenReturn(true);

        assertThrows(BusinessValidationException.class, () -> service.eliminarCurso(100L));
        verify(cursos, never()).delete(any());
    }

    @Test
    void eliminarCursoQueYaNoEstaEnBorradorLanzaValidacion() {
        Curso curso = cursoEnEstado("PUBLICADO");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class, () -> service.eliminarCurso(100L));
        verify(cursos, never()).delete(any());
    }

    // ---------------------------------------------------------------- Transiciones automáticas --

    @Test
    void transicionesAutomaticasPasanPublicadoAEnCursoAlLlegarLaFecha() {
        Curso curso = cursoEnEstado("PUBLICADO");
        when(cursos.findByEstadoCurso_CodigoAndFechaInicioLessThanEqual("PUBLICADO", LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(curso));
        when(cursos.findByEstadoCurso_CodigoAndFechaFinLessThan("EN_CURSO", LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of());
        EstadoCurso enCurso = new EstadoCurso();
        enCurso.setCodigo("EN_CURSO");
        when(estadosCurso.findByCodigo("EN_CURSO")).thenReturn(Optional.of(enCurso));

        service.aplicarTransicionesAutomaticas();

        assertEquals("EN_CURSO", curso.getEstadoCurso().getCodigo());
    }

    @Test
    void transicionesAutomaticasCierranUnCursoEnCursoAlFinalizarSuFecha() {
        Curso curso = cursoEnEstado("EN_CURSO");
        when(cursos.findByEstadoCurso_CodigoAndFechaInicioLessThanEqual("PUBLICADO", LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of());
        when(cursos.findByEstadoCurso_CodigoAndFechaFinLessThan("EN_CURSO", LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(curso));
        EstadoCurso cerrado = new EstadoCurso();
        cerrado.setCodigo("CERRADO");
        when(estadosCurso.findByCodigo("CERRADO")).thenReturn(Optional.of(cerrado));

        service.aplicarTransicionesAutomaticas();

        assertEquals("CERRADO", curso.getEstadoCurso().getCodigo());
    }
}
