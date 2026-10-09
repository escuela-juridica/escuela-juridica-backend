package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
import pe.edu.utp.escuela.app.dto.ActualizarSesionPeticion;
import pe.edu.utp.escuela.app.dto.CrearLeccionPeticion;
import pe.edu.utp.escuela.app.dto.CrearModuloPeticion;
import pe.edu.utp.escuela.app.dto.LeccionRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloRespuesta;
import pe.edu.utp.escuela.app.dto.OrdenPeticion;
import pe.edu.utp.escuela.app.dto.TipoLeccion;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.ExamenRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.OpcionPreguntaRepositorio;
import pe.edu.utp.escuela.app.repository.PreguntaRepositorio;
import pe.edu.utp.escuela.app.repository.RecursoRepositorio;
import pe.edu.utp.escuela.app.repository.TipoMaterialRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-011 (organizar módulos/lecciones/materiales) y HU-012 (programar sesiones en vivo), ambas
 * implementadas por {@link ContenidoServicio}. */
@ExtendWith(MockitoExtension.class)
class ContenidoServicioTests {

    @Mock private CursoRepositorio cursos;
    @Mock private ModuloRepositorio modulos;
    @Mock private LeccionRepositorio lecciones;
    @Mock private MaterialLeccionRepositorio materiales;
    @Mock private RecursoRepositorio recursos;
    @Mock private TipoMaterialRepositorio tiposMaterial;
    @Mock private ExamenRepositorio examenes;
    @Mock private PreguntaRepositorio preguntas;
    @Mock private OpcionPreguntaRepositorio opciones;
    @Mock private ArchivoAlmacenamientoServicio almacenamiento;
    @Mock private CurrentUserService currentUserService;

    private ContenidoServicio servicio;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("America/Lima"));
        servicio = new ContenidoServicio(cursos, modulos, lecciones, materiales, recursos, tiposMaterial,
                examenes, preguntas, opciones, almacenamiento, currentUserService, new TextNormalizer(), clock);
    }

    private void comoAdministrador() {
        when(currentUserService.get())
                .thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    private Curso cursoEnEstado(String codigo) {
        EstadoCurso estado = new EstadoCurso();
        estado.setCodigo(codigo);
        Curso curso = new Curso();
        curso.setId(100L);
        curso.setTitulo("Curso de prueba");
        curso.setModalidad("EN_VIVO");
        curso.setFechaInicio(java.time.LocalDate.of(2026, 9, 1));
        curso.setFechaFin(java.time.LocalDate.of(2026, 10, 1));
        curso.setEstadoCurso(estado);
        return curso;
    }

    private Modulo moduloDe(Curso curso) {
        Modulo modulo = new Modulo();
        modulo.setId(10L);
        modulo.setCurso(curso);
        modulo.setTitulo("Módulo 1");
        modulo.setOrden(1);
        modulo.setActivo(true);
        return modulo;
    }

    private Leccion leccionEnVivoDe(Modulo modulo) {
        Leccion leccion = new Leccion();
        leccion.setId(20L);
        leccion.setModulo(modulo);
        leccion.setTitulo("Sesión en vivo");
        leccion.setOrden(1);
        leccion.setTipo(TipoLeccion.EN_VIVO.name());
        leccion.setEstado("PROGRAMADA");
        leccion.setEsObligatoria(true);
        leccion.setActivo(true);
        return leccion;
    }

    // ---------------------------------------------------------------- Módulos (HU-011) --

    @Test
    void crearModuloConTituloValidoLoCreaOrdenadoComoSiguiente() {
        comoAdministrador();
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        when(modulos.countByCurso_Id(100L)).thenReturn(2L);

        ModuloRespuesta respuesta = servicio.crearModulo(100L, new CrearModuloPeticion("Módulo 3", null));

        assertEquals("Módulo 3", respuesta.titulo());
        assertEquals(3, respuesta.orden());
        assertTrue(respuesta.activo());
    }

    @Test
    void crearModuloConTituloVacioLanzaValidacion() {
        comoAdministrador();
        when(cursos.findById(100L)).thenReturn(Optional.of(cursoEnEstado("BORRADOR")));

        assertThrows(BusinessValidationException.class,
                () -> servicio.crearModulo(100L, new CrearModuloPeticion("   ", null)));
    }

    @Test
    void crearModuloSinRolAdministradorLanzaForbidden() {
        when(currentUserService.get())
                .thenReturn(new CurrentUser(2L, "alumno@example.com", Set.of("ALUMNO")));

        assertThrows(ForbiddenException.class,
                () -> servicio.crearModulo(100L, new CrearModuloPeticion("Módulo", null)));
    }

    @Test
    void cambiarActivoModuloDesactivaCuandoElCursoNoHaIniciado() {
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));
        when(lecciones.findByModulo_IdOrderByOrdenAsc(10L)).thenReturn(List.of());

        ModuloRespuesta respuesta = servicio.cambiarActivoModulo(10L, false);

        assertFalse(respuesta.activo());
    }

    @Test
    void cambiarActivoModuloARetirarConCursoYaIniciadoLanzaValidacion() {
        // HU-011 Escenario 3 (conservación histórica): un módulo en uso no se retira tras iniciar.
        comoAdministrador();
        Curso curso = cursoEnEstado("EN_CURSO");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));

        assertThrows(BusinessValidationException.class, () -> servicio.cambiarActivoModulo(10L, false));
    }

    @Test
    void reactivarModuloConCursoIniciadoNoEstaRestringido() {
        comoAdministrador();
        Curso curso = cursoEnEstado("EN_CURSO");
        Modulo modulo = moduloDe(curso);
        modulo.setActivo(false);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));
        when(lecciones.findByModulo_IdOrderByOrdenAsc(10L)).thenReturn(List.of());

        ModuloRespuesta respuesta = servicio.cambiarActivoModulo(10L, true);

        assertTrue(respuesta.activo());
    }

    @Test
    void reordenarModulosConListaCompletaConservaSecuenciaUnica() {
        // HU-011 Escenario 1 (estructura ordenada).
        comoAdministrador();
        Curso curso = cursoEnEstado("BORRADOR");
        when(cursos.findById(100L)).thenReturn(Optional.of(curso));
        Modulo m1 = moduloDe(curso);
        Modulo m2 = moduloDe(curso);
        m2.setId(11L);
        m2.setOrden(2);
        when(modulos.findByCurso_IdOrderByOrdenAsc(100L)).thenReturn(List.of(m1, m2));

        List<ModuloRespuesta> resultado = servicio.reordenarModulos(100L, new OrdenPeticion(List.of(11L, 10L)));

        assertEquals(1, m2.getOrden());
        assertEquals(2, m1.getOrden());
        assertEquals(2, resultado.size());
    }

    @Test
    void reordenarModulosConListaIncompletaLanzaValidacion() {
        comoAdministrador();
        when(cursos.findById(100L)).thenReturn(Optional.of(cursoEnEstado("BORRADOR")));
        Modulo m1 = moduloDe(cursoEnEstado("BORRADOR"));
        Modulo m2 = moduloDe(cursoEnEstado("BORRADOR"));
        m2.setId(11L);
        when(modulos.findByCurso_IdOrderByOrdenAsc(100L)).thenReturn(List.of(m1, m2));

        assertThrows(BusinessValidationException.class,
                () -> servicio.reordenarModulos(100L, new OrdenPeticion(List.of(10L))));
    }

    // ---------------------------------------------------------------- Lecciones (HU-011) --

    @Test
    void crearLeccionGrabadaSoloPideLosDatosDeSuTipo() {
        // HU-011 Escenario 2 (tipos de lección): una lección GRABADA no exige datos de sesión.
        comoAdministrador();
        Curso curso = cursoEnEstado("BORRADOR");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));
        when(lecciones.countByModulo_Id(10L)).thenReturn(0L);

        LeccionRespuesta respuesta = servicio.crearLeccion(10L,
                new CrearLeccionPeticion("Introducción", null, TipoLeccion.GRABADA, true, false));

        assertEquals("GRABADA", respuesta.tipo());
        assertEquals(1, respuesta.orden());
    }

    @Test
    void crearLeccionEnVivoNaceSinProgramar() {
        comoAdministrador();
        Curso curso = cursoEnEstado("BORRADOR");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));
        when(lecciones.countByModulo_Id(10L)).thenReturn(0L);

        LeccionRespuesta respuesta = servicio.crearLeccion(10L,
                new CrearLeccionPeticion("Sesión en vivo", null, TipoLeccion.EN_VIVO, true, false));

        assertEquals("EN_VIVO", respuesta.tipo());
        assertEquals(null, respuesta.fechaHoraInicio());
    }

    @Test
    void crearLeccionObligatoriaConCursoYaIniciadoLanzaValidacion() {
        comoAdministrador();
        Curso curso = cursoEnEstado("EN_CURSO");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));

        assertThrows(BusinessValidationException.class, () -> servicio.crearLeccion(10L,
                new CrearLeccionPeticion("Nueva lección", null, TipoLeccion.GRABADA, true, false)));
    }

    @Test
    void crearLeccionComplementariaConCursoYaIniciadoSiEstaPermitido() {
        comoAdministrador();
        Curso curso = cursoEnEstado("EN_CURSO");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));
        when(lecciones.countByModulo_Id(10L)).thenReturn(1L);

        LeccionRespuesta respuesta = servicio.crearLeccion(10L,
                new CrearLeccionPeticion("Material extra", null, TipoLeccion.GRABADA, false, false));

        assertFalse(respuesta.esObligatoria());
    }

    @Test
    void unaLeccionEnVivoNoPuedeMarcarseComoVistaPrevia() {
        comoAdministrador();
        Curso curso = cursoEnEstado("BORRADOR");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));

        assertThrows(BusinessValidationException.class, () -> servicio.crearLeccion(10L,
                new CrearLeccionPeticion("Sesión", null, TipoLeccion.EN_VIVO, true, true)));
    }

    @Test
    void cambiarActivoLeccionARetirarConCursoIniciadoLanzaValidacion() {
        comoAdministrador();
        Curso curso = cursoEnEstado("EN_CURSO");
        Leccion leccion = leccionEnVivoDe(moduloDe(curso));
        when(lecciones.findById(20L)).thenReturn(Optional.of(leccion));

        assertThrows(BusinessValidationException.class, () -> servicio.cambiarActivoLeccion(20L, false));
    }

    // ---------------------------------------------------------------- Sesiones (HU-012) --

    @Test
    void actualizarSesionConDatosValidosQuedaProgramada() {
        // HU-012 Escenario 1 (programación válida).
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        Leccion leccion = leccionEnVivoDe(moduloDe(curso));
        when(lecciones.findById(20L)).thenReturn(Optional.of(leccion));
        when(materiales.findByLeccion_IdOrderByOrdenAsc(20L)).thenReturn(List.of());

        Instant inicio = Instant.parse("2026-09-20T18:00:00Z");
        Instant fin = Instant.parse("2026-09-20T20:00:00Z");
        LeccionRespuesta respuesta = servicio.actualizarSesion(20L,
                new ActualizarSesionPeticion(inicio, fin, "https://meet.example.com/sesion"));

        assertEquals(inicio, respuesta.fechaHoraInicio());
        assertEquals(fin, respuesta.fechaHoraFin());
        assertEquals("https://meet.example.com/sesion", respuesta.enlaceReunion());
    }

    @Test
    void actualizarSesionReprogramaConservaLaNuevaProgramacion() {
        // HU-012 Escenario 2 (reprogramación).
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        Leccion leccion = leccionEnVivoDe(moduloDe(curso));
        leccion.setFechaHoraInicio(Instant.parse("2026-09-18T18:00:00Z"));
        leccion.setFechaHoraFin(Instant.parse("2026-09-18T20:00:00Z"));
        leccion.setEnlaceReunion("https://meet.example.com/original");
        when(lecciones.findById(20L)).thenReturn(Optional.of(leccion));
        when(materiales.findByLeccion_IdOrderByOrdenAsc(20L)).thenReturn(List.of());

        Instant nuevoInicio = Instant.parse("2026-09-22T18:00:00Z");
        Instant nuevoFin = Instant.parse("2026-09-22T20:00:00Z");
        LeccionRespuesta respuesta = servicio.actualizarSesion(20L,
                new ActualizarSesionPeticion(nuevoInicio, nuevoFin, "https://meet.example.com/nueva"));

        assertEquals(nuevoInicio, respuesta.fechaHoraInicio());
        assertEquals("https://meet.example.com/nueva", respuesta.enlaceReunion());
    }

    @Test
    void actualizarSesionSobreLeccionNoEnVivoLanzaValidacion() {
        // HU-012 Escenario 3 (datos obligatorios / operación rechazada).
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        Modulo modulo = moduloDe(curso);
        Leccion leccion = leccionEnVivoDe(modulo);
        leccion.setTipo(TipoLeccion.GRABADA.name());
        when(lecciones.findById(20L)).thenReturn(Optional.of(leccion));

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarSesion(20L,
                new ActualizarSesionPeticion(Instant.parse("2026-09-20T18:00:00Z"),
                        Instant.parse("2026-09-20T20:00:00Z"), "https://meet.example.com/sesion")));
    }

    @Test
    void actualizarSesionEnCursoVirtualLanzaValidacion() {
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        curso.setModalidad("VIRTUAL");
        Leccion leccion = leccionEnVivoDe(moduloDe(curso));
        when(lecciones.findById(20L)).thenReturn(Optional.of(leccion));

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarSesion(20L,
                new ActualizarSesionPeticion(Instant.parse("2026-09-20T18:00:00Z"),
                        Instant.parse("2026-09-20T20:00:00Z"), "https://meet.example.com/sesion")));
    }

    @Test
    void actualizarSesionConFinAnteriorAlInicioLanzaValidacion() {
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        Leccion leccion = leccionEnVivoDe(moduloDe(curso));
        when(lecciones.findById(20L)).thenReturn(Optional.of(leccion));

        assertThrows(BusinessValidationException.class, () -> servicio.actualizarSesion(20L,
                new ActualizarSesionPeticion(Instant.parse("2026-09-20T20:00:00Z"),
                        Instant.parse("2026-09-20T18:00:00Z"), "https://meet.example.com/sesion")));
    }

    @Test
    void actualizarSesionFueraDelPeriodoDelCursoLanzaValidacion() {
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        Leccion leccion = leccionEnVivoDe(moduloDe(curso));
        when(lecciones.findById(20L)).thenReturn(Optional.of(leccion));

        // El curso va del 1 de septiembre al 1 de octubre; esta sesión cae en noviembre.
        assertThrows(BusinessValidationException.class, () -> servicio.actualizarSesion(20L,
                new ActualizarSesionPeticion(Instant.parse("2026-11-05T18:00:00Z"),
                        Instant.parse("2026-11-05T20:00:00Z"), "https://meet.example.com/sesion")));
    }

    // ---------------------------------------------------------------- Eliminar (HU-016 apoyo) --

    @Test
    void eliminarModuloConCursoEnBorradorLoElimina() {
        comoAdministrador();
        Curso curso = cursoEnEstado("BORRADOR");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));
        when(lecciones.findByModulo_IdOrderByOrdenAsc(10L)).thenReturn(List.of());
        when(examenes.findByModulo_IdOrderByOrdenAsc(10L)).thenReturn(List.of());

        servicio.eliminarModulo(10L);

        org.mockito.Mockito.verify(modulos).delete(modulo);
    }

    @Test
    void eliminarModuloConCursoYaPublicadoLanzaValidacion() {
        comoAdministrador();
        Curso curso = cursoEnEstado("PUBLICADO");
        Modulo modulo = moduloDe(curso);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));

        assertThrows(BusinessValidationException.class, () -> servicio.eliminarModulo(10L));
    }
}
