package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.dto.ActualizarReglasCursoPeticion;
import pe.edu.utp.escuela.app.dto.ReglasCursoRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.repository.CategoriaTematicaRepositorio;
import pe.edu.utp.escuela.app.repository.CursoDocenteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoFirmanteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.EntidadCertificadoraRepositorio;
import pe.edu.utp.escuela.app.repository.ExamenRepositorio;
import pe.edu.utp.escuela.app.repository.EstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.FirmanteRepositorio;
import pe.edu.utp.escuela.app.repository.HistorialEstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.PersonaRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.repository.TipoCursoRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-014 — Configurar requisitos académicos y de certificación ({@link
 * CursoServicio#actualizarReglas}). El resto de {@link CursoServicio} (HU-010) ya tiene su propia
 * cobertura funcional en EP02; esta clase se limita a HU-014. */
@ExtendWith(MockitoExtension.class)
class CursoServicioReglasTests {

    @Mock private CursoRepositorio cursos;
    @Mock private CursoDocenteRepositorio cursoDocentes;
    @Mock private CursoFirmanteRepositorio cursoFirmantes;
    @Mock private ReglaCursoRepositorio reglasCurso;
    @Mock private ExamenRepositorio examenes;
    @Mock private HistorialEstadoCursoRepositorio historial;
    @Mock private EstadoCursoRepositorio estadosCurso;
    @Mock private TipoCursoRepositorio tiposCurso;
    @Mock private CategoriaTematicaRepositorio categorias;
    @Mock private EntidadCertificadoraRepositorio entidades;
    @Mock private PersonaRepositorio personas;
    @Mock private FirmanteRepositorio firmantes;
    @Mock private MatriculaRepositorio matriculas;
    @Mock private CurrentUserService currentUserService;

    private CursoServicio servicio;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("America/Lima"));
        servicio = new CursoServicio(cursos, cursoDocentes, cursoFirmantes, reglasCurso, examenes, historial,
                estadosCurso, tiposCurso, categorias, entidades, personas, firmantes, matriculas,
                currentUserService, new TextNormalizer(), clock);
        when(currentUserService.get())
                .thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    private Curso cursoVirtualEnEstado(String codigo) {
        EstadoCurso estado = new EstadoCurso();
        estado.setCodigo(codigo);
        Curso curso = new Curso();
        curso.setId(100L);
        curso.setModalidad("VIRTUAL");
        curso.setFechaFin(null);
        curso.setEstadoCurso(estado);
        return curso;
    }

    private ReglaCurso reglaDe(Curso curso) {
        ReglaCurso regla = new ReglaCurso();
        regla.setCurso(curso);
        regla.setBloqueadoEn(null);
        return regla;
    }

    private ActualizarReglasCursoPeticion peticionValida() {
        return new ActualizarReglasCursoPeticion(true, true, false,
                BigDecimal.valueOf(12), BigDecimal.valueOf(14),
                BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.valueOf(80),
                true, 3, null);
    }

    @Test
    void actualizarReglasConCondicionesActivasSoloExigeLosUmbralesSeleccionados() {
        // HU-014 Escenario 1 (condiciones activas).
        Curso curso = cursoVirtualEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(reglaDe(curso)));
        when(examenes.existsByCurso_IdAndActivoTrueAndTipo(100L, "CALIFICADO")).thenReturn(false);

        // Solo exige progreso: ni examen ni asistencia.
        ActualizarReglasCursoPeticion peticion = new ActualizarReglasCursoPeticion(false, true, false,
                BigDecimal.valueOf(12), BigDecimal.valueOf(14),
                BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.valueOf(80),
                true, 3, null);

        ReglasCursoRespuesta respuesta = servicio.actualizarReglas(100L, peticion);

        assertEquals(false, respuesta.requiereExamenes());
        assertEquals(true, respuesta.requiereProgreso());
        assertEquals(false, respuesta.requiereAsistencia());
    }

    @Test
    void actualizarReglasConNotaRefrendadoMenorOIgualQueNotaMinimaLanzaValidacion() {
        // HU-014 Escenario 2 (umbral fuera de rango / relación inválida).
        Curso curso = cursoVirtualEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(reglaDe(curso)));

        ActualizarReglasCursoPeticion peticion = new ActualizarReglasCursoPeticion(true, true, false,
                BigDecimal.valueOf(14), BigDecimal.valueOf(14),
                BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.valueOf(80),
                true, 3, null);

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarReglas(100L, peticion));
    }

    @Test
    void actualizarReglasEnCursoVirtualExigiendoAsistenciaLanzaValidacion() {
        Curso curso = cursoVirtualEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(reglaDe(curso)));

        ActualizarReglasCursoPeticion peticion = new ActualizarReglasCursoPeticion(true, true, true,
                BigDecimal.valueOf(12), BigDecimal.valueOf(14),
                BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.valueOf(80),
                true, 3, LocalDate.of(2026, 10, 1));

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarReglas(100L, peticion));
    }

    @Test
    void actualizarReglasConLasReglasYaCongeladasLanzaValidacion() {
        Curso curso = cursoVirtualEnEstado("BORRADOR");
        ReglaCurso regla = reglaDe(curso);
        regla.setBloqueadoEn(Instant.parse("2026-09-01T00:00:00Z"));
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(regla));

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarReglas(100L, peticionValida()));
    }

    @Test
    void actualizarReglasConCursoYaIniciadoLanzaValidacion() {
        Curso curso = cursoVirtualEnEstado("EN_CURSO");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(reglaDe(curso)));

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarReglas(100L, peticionValida()));
    }

    @Test
    void actualizarReglasDesactivandoExamenesConCalificadoActivoLanzaValidacion() {
        Curso curso = cursoVirtualEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(reglaDe(curso)));
        when(examenes.existsByCurso_IdAndActivoTrueAndTipo(100L, "CALIFICADO")).thenReturn(true);

        ActualizarReglasCursoPeticion peticion = new ActualizarReglasCursoPeticion(false, true, false,
                BigDecimal.valueOf(12), BigDecimal.valueOf(14),
                BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.valueOf(80),
                true, 3, null);

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarReglas(100L, peticion));
    }

    @Test
    void actualizarReglasConEsperaYSecuenciaObligatoriaQuedanDisponibles() {
        // HU-014 Escenario 3 (emisión y recorrido).
        Curso curso = cursoVirtualEnEstado("BORRADOR");
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(reglaDe(curso)));
        when(examenes.existsByCurso_IdAndActivoTrueAndTipo(100L, "CALIFICADO")).thenReturn(false);

        ActualizarReglasCursoPeticion peticion = new ActualizarReglasCursoPeticion(false, true, false,
                BigDecimal.valueOf(12), BigDecimal.valueOf(14),
                BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.valueOf(80),
                true, 5, null);

        ReglasCursoRespuesta respuesta = servicio.actualizarReglas(100L, peticion);

        assertEquals(true, respuesta.secuenciaObligatoria());
        assertEquals(5, respuesta.diasEsperaCertificado());
    }

    @Test
    void actualizarReglasSinModalidadDefinidaLanzaValidacion() {
        Curso curso = cursoVirtualEnEstado("BORRADOR");
        curso.setModalidad(null);
        when(cursos.findWithDetalleById(100L)).thenReturn(Optional.of(curso));
        when(reglasCurso.findByCurso_Id(100L)).thenReturn(Optional.of(reglaDe(curso)));

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarReglas(100L, peticionValida()));
    }
}
