package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pe.edu.utp.escuela.app.dto.CancelarMatriculaPeticion;
import pe.edu.utp.escuela.app.dto.CondicionCuentaAdmin;
import pe.edu.utp.escuela.app.dto.CrearMatriculaAdministrativaPeticion;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.RolUsuarioAdmin;
import pe.edu.utp.escuela.app.dto.UsuarioAdminRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.HistorialEstadoMatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.NotificacionRepositorio;
import pe.edu.utp.escuela.app.repository.PagoRepositorio;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepositorio;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;

/** HU-017 (matrícula gratuita), HU-019 (matrícula administrativa), HU-020 (consultar/controlar
 * matrículas y pagos), HU-021 (mis cursos y accesos) y el filtrado de HU-041 (reporte). */
@ExtendWith(MockitoExtension.class)
class MatriculaServicioTests {

    @Mock private MatriculaRepositorio matriculas;
    @Mock private CursoRepositorio cursos;
    @Mock private UsuarioRepositorio usuarios;
    @Mock private UsuarioRolRepositorio roles;
    @Mock private AdminUsuariosServicio adminUsuarios;
    @Mock private PagoRepositorio pagos;
    @Mock private HistorialEstadoMatriculaRepositorio historial;
    @Mock private NotificacionRepositorio notificaciones;
    @Mock private ReglaCursoRepositorio reglas;
    @Mock private ModuloRepositorio modulos;
    @Mock private LeccionRepositorio lecciones;
    @Mock private ProgresoLeccionRepositorio progresoLecciones;
    @Mock private MailService mailService;
    @Mock private CurrentUserService actual;

    private MatriculaServicio servicio;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("America/Lima"));

    @BeforeEach
    void setUp() {
        servicio = new MatriculaServicio(matriculas, cursos, usuarios, roles, adminUsuarios, pagos,
                historial, notificaciones, reglas, modulos, lecciones, progresoLecciones, mailService, actual, clock);
        lenient().when(cursos.bloquearParaMatricula(any()))
                .thenAnswer(invocation -> cursos.findWithDetalleById(invocation.getArgument(0)));
    }

    private void comoAlumno(Long id) {
        when(actual.get()).thenReturn(new CurrentUser(id, "alumno@example.com", Set.of("ALUMNO")));
    }

    private void comoAdministrador(Long id) {
        when(actual.get()).thenReturn(new CurrentUser(id, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    private Usuario alumnoHabilitado(Long id) {
        Persona persona = new Persona();
        persona.setNombres("Ana");
        persona.setApellidoPaterno("Pérez");
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPersona(persona);
        usuario.setCorreo("ana@example.com");
        usuario.setActivo(true);
        usuario.setCorreoVerificadoEn(Instant.parse("2026-08-01T00:00:00Z"));
        usuario.setRequiereCambioContrasena(false);
        return usuario;
    }

    private Curso cursoPublicadoGratuito() {
        EstadoCurso estado = new EstadoCurso();
        estado.setCodigo("PUBLICADO");
        Curso curso = new Curso();
        curso.setId(200L);
        curso.setTitulo("Curso gratuito");
        curso.setUrlAmigable("curso-gratuito");
        curso.setModalidad("VIRTUAL");
        curso.setTipoVenta("GRATUITO");
        curso.setEstadoCurso(estado);
        return curso;
    }

    private UsuarioAdminRespuesta respuestaAdminCon(List<RolUsuarioAdmin> rolesDelUsuario) {
        return new UsuarioAdminRespuesta(5L, "Ana", "Pérez", null, "Ana Pérez", "ana@example.com",
                null, null, "FORMULARIO", true, CondicionCuentaAdmin.NINGUNA,
                rolesDelUsuario.get(0), rolesDelUsuario, Instant.parse("2026-08-01T00:00:00Z"), null);
    }

    // ---------------------------------------------------------------- HU-017 --

    @Test
    void matricularGratisConCursoPublicadoCreaMatriculaActiva() {
        // HU-017 Escenario 1 (matrícula gratuita).
        comoAlumno(1L);
        Usuario usuario = alumnoHabilitado(1L);
        Curso curso = cursoPublicadoGratuito();
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(usuario));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(curso));
        when(matriculas.existsByUsuario_IdAndCurso_Id(1L, 200L)).thenReturn(false);

        MatriculaRespuesta respuesta = servicio.matricularGratis(200L);

        assertEquals("ACTIVA", respuesta.estado());
        assertEquals("GRATUITA", respuesta.formaIngreso());
        assertTrue(respuesta.accesoEfectivo());
    }

    @Test
    void matricularGratisConMatriculaExistenteNoCreaUnSegundoRegistro() {
        // HU-017 Escenario 2 (idempotencia).
        comoAlumno(1L);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(alumnoHabilitado(1L)));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(cursoPublicadoGratuito()));
        when(matriculas.existsByUsuario_IdAndCurso_Id(1L, 200L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> servicio.matricularGratis(200L));
    }

    @Test
    void matricularGratisConCursoNoPublicadoLanzaValidacion() {
        // HU-017 Escenario 3 (disponibilidad).
        comoAlumno(1L);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(alumnoHabilitado(1L)));
        Curso curso = cursoPublicadoGratuito();
        curso.getEstadoCurso().setCodigo("BORRADOR");
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(curso));
        when(matriculas.existsByUsuario_IdAndCurso_Id(1L, 200L)).thenReturn(false);

        assertThrows(BusinessValidationException.class, () -> servicio.matricularGratis(200L));
    }

    @Test
    void matricularGratisConCursoSinCupoLanzaValidacion() {
        comoAlumno(1L);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(alumnoHabilitado(1L)));
        Curso curso = cursoPublicadoGratuito();
        curso.setCupoMaximo(10);
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(curso));
        when(matriculas.existsByUsuario_IdAndCurso_Id(1L, 200L)).thenReturn(false);
        // contarActivas() es un método default del repositorio: un mock no ejecuta su cuerpo real
        // (que delega en contarActivasPorCurso), así que se estuba directamente.
        when(matriculas.contarActivas(List.of(200L))).thenReturn(java.util.Map.of(200L, 10L));

        assertThrows(BusinessValidationException.class, () -> servicio.matricularGratis(200L));
    }

    @Test
    void matricularGratisConCursoNoGratuitoLanzaValidacion() {
        comoAlumno(1L);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(alumnoHabilitado(1L)));
        Curso curso = cursoPublicadoGratuito();
        curso.setTipoVenta("PAGADO");
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(curso));

        assertThrows(BusinessValidationException.class, () -> servicio.matricularGratis(200L));
    }

    @Test
    void matricularGratisConCuentaNoHabilitadaLanzaValidacion() {
        comoAlumno(1L);
        Usuario usuario = alumnoHabilitado(1L);
        usuario.setCorreoVerificadoEn(null);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(usuario));

        assertThrows(BusinessValidationException.class, () -> servicio.matricularGratis(200L));
    }

    @Test
    void matricularGratisSinRolAlumnoLanzaValidacion() {
        when(actual.get()).thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(alumnoHabilitado(1L)));

        assertThrows(BusinessValidationException.class, () -> servicio.matricularGratis(200L));
    }

    // ---------------------------------------------------------------- HU-019 --

    private CrearMatriculaAdministrativaPeticion peticionExonerada() {
        return new CrearMatriculaAdministrativaPeticion(5L, 200L, "EXONERADO", BigDecimal.ZERO, null, null,
                "Convenio institucional", false);
    }

    private void prepararPersistenciaDeMatricula() {
        when(matriculas.saveAndFlush(any(Matricula.class))).thenAnswer(invocation -> {
            Matricula m = invocation.getArgument(0);
            m.setId(500L);
            return m;
        });
    }

    @Test
    void matricularAdministrativamenteConAlumnoValidoCreaMatricula() {
        // HU-019 Escenario 1 (selección de alumno).
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(5L)).thenReturn(Optional.of(alumnoHabilitado(5L)));
        when(adminUsuarios.obtener(5L)).thenReturn(respuestaAdminCon(List.of(RolUsuarioAdmin.ALUMNO)));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(cursoPublicadoGratuito()));
        when(matriculas.existsByUsuario_IdAndCurso_Id(5L, 200L)).thenReturn(false);
        prepararPersistenciaDeMatricula();

        MatriculaRespuesta respuesta = servicio.matricularAdministrativamente(peticionExonerada());

        assertEquals("ACTIVA", respuesta.estado());
        assertEquals("ADMINISTRADOR", respuesta.formaIngreso());
    }

    @Test
    void matricularAdministrativamenteConUsuarioSoloAdministradorNoApareceComoCandidato() {
        // HU-019 Escenario 2 (exclusión por rol).
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(5L)).thenReturn(Optional.of(alumnoHabilitado(5L)));
        when(adminUsuarios.obtener(5L)).thenReturn(respuestaAdminCon(List.of(RolUsuarioAdmin.ADMINISTRADOR)));

        assertThrows(BusinessValidationException.class,
                () -> servicio.matricularAdministrativamente(peticionExonerada()));
    }

    @Test
    void matricularAdministrativamenteConMatriculaPreviaConservaUnSoloRegistro() {
        // HU-019 Escenario 3 (registro sin duplicidad).
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(5L)).thenReturn(Optional.of(alumnoHabilitado(5L)));
        when(adminUsuarios.obtener(5L)).thenReturn(respuestaAdminCon(List.of(RolUsuarioAdmin.ALUMNO)));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(cursoPublicadoGratuito()));
        when(matriculas.existsByUsuario_IdAndCurso_Id(5L, 200L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> servicio.matricularAdministrativamente(peticionExonerada()));
    }

    @Test
    void matricularAdministrativamenteConCondicionEconomicaInvalidaLanzaValidacion() {
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(5L)).thenReturn(Optional.of(alumnoHabilitado(5L)));
        when(adminUsuarios.obtener(5L)).thenReturn(respuestaAdminCon(List.of(RolUsuarioAdmin.ALUMNO)));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(cursoPublicadoGratuito()));

        CrearMatriculaAdministrativaPeticion peticion = new CrearMatriculaAdministrativaPeticion(5L, 200L,
                "DESCUENTO", null, null, null, "Motivo", false);

        assertThrows(BusinessValidationException.class, () -> servicio.matricularAdministrativamente(peticion));
    }

    @Test
    void matricularAdministrativamenteExoneradoConImporteDistintoDeCeroLanzaValidacion() {
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(5L)).thenReturn(Optional.of(alumnoHabilitado(5L)));
        when(adminUsuarios.obtener(5L)).thenReturn(respuestaAdminCon(List.of(RolUsuarioAdmin.ALUMNO)));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(cursoPublicadoGratuito()));
        when(matriculas.existsByUsuario_IdAndCurso_Id(5L, 200L)).thenReturn(false);
        prepararPersistenciaDeMatricula();

        CrearMatriculaAdministrativaPeticion peticion = new CrearMatriculaAdministrativaPeticion(5L, 200L,
                "EXONERADO", BigDecimal.TEN, null, null, "Motivo", false);

        assertThrows(BusinessValidationException.class, () -> servicio.matricularAdministrativamente(peticion));
    }

    @Test
    void matricularAdministrativamenteManualSinDatosCompletosLanzaValidacion() {
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(5L)).thenReturn(Optional.of(alumnoHabilitado(5L)));
        when(adminUsuarios.obtener(5L)).thenReturn(respuestaAdminCon(List.of(RolUsuarioAdmin.ALUMNO)));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(cursoPublicadoGratuito()));
        when(matriculas.existsByUsuario_IdAndCurso_Id(5L, 200L)).thenReturn(false);
        prepararPersistenciaDeMatricula();

        CrearMatriculaAdministrativaPeticion peticion = new CrearMatriculaAdministrativaPeticion(5L, 200L,
                "REGISTRADO_MANUAL", BigDecimal.valueOf(150), null, null, "Pago por Yape", false);

        assertThrows(BusinessValidationException.class, () -> servicio.matricularAdministrativamente(peticion));
    }

    @Test
    void matricularAdministrativamenteManualConDatosCompletosRegistraElPago() {
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(5L)).thenReturn(Optional.of(alumnoHabilitado(5L)));
        when(adminUsuarios.obtener(5L)).thenReturn(respuestaAdminCon(List.of(RolUsuarioAdmin.ALUMNO)));
        when(cursos.findWithDetalleById(200L)).thenReturn(Optional.of(cursoPublicadoGratuito()));
        when(matriculas.existsByUsuario_IdAndCurso_Id(5L, 200L)).thenReturn(false);
        prepararPersistenciaDeMatricula();

        CrearMatriculaAdministrativaPeticion peticion = new CrearMatriculaAdministrativaPeticion(5L, 200L,
                "REGISTRADO_MANUAL", BigDecimal.valueOf(150), "Yape", "OP-123", "Pago por Yape", false);

        MatriculaRespuesta respuesta = servicio.matricularAdministrativamente(peticion);

        assertEquals("ACTIVA", respuesta.estado());
        org.mockito.Mockito.verify(pagos).save(any());
    }

    // ---------------------------------------------------------------- HU-020 --

    @Test
    void listarAdministrativasDelegaFiltrosAlRepositorio() {
        // HU-020 Escenario 1 (consulta administrativa).
        comoAdministrador(1L);
        Pageable pageable = PageRequest.of(0, 20);
        @SuppressWarnings("unchecked")
        Page<MatriculaAdministrativaRespuesta> pagina = org.mockito.Mockito.mock(Page.class);
        when(matriculas.buscarAdministrativas("curso jurídico", "ACTIVA", pageable)).thenReturn(pagina);

        Page<MatriculaAdministrativaRespuesta> resultado =
                servicio.listarAdministrativas(" Curso Jurídico ", "activa", pageable);

        assertEquals(pagina, resultado);
    }

    @Test
    void cancelarUnaMatriculaActivaLaCancela() {
        comoAdministrador(1L);
        Matricula m = new Matricula();
        m.setId(500L);
        m.setEstado("ACTIVA");
        m.setCurso(cursoPublicadoGratuito());
        m.setUsuario(alumnoHabilitado(5L));
        when(matriculas.findWithDetalleById(500L)).thenReturn(Optional.of(m));

        MatriculaRespuesta respuesta = servicio.cancelar(500L, new CancelarMatriculaPeticion("Ya no continuará"));

        assertEquals("CANCELADA", respuesta.estado());
    }

    @Test
    void cancelarUnaMatriculaYaCanceladaLanzaValidacion() {
        // HU-020 Escenario 3 (cambio controlado).
        comoAdministrador(1L);
        Matricula m = new Matricula();
        m.setId(500L);
        m.setEstado("CANCELADA");
        when(matriculas.findWithDetalleById(500L)).thenReturn(Optional.of(m));

        assertThrows(BusinessValidationException.class,
                () -> servicio.cancelar(500L, new CancelarMatriculaPeticion("Motivo")));
    }

    @Test
    void cancelarUnaMatriculaInexistenteLanzaNoEncontrado() {
        comoAdministrador(1L);
        when(matriculas.findWithDetalleById(500L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> servicio.cancelar(500L, new CancelarMatriculaPeticion("Motivo")));
    }

    // ---------------------------------------------------------------- HU-021 --

    @Test
    void misCursosConMatriculaActivaYVigenteOfreceAccesoDisponible() {
        // HU-021 Escenario 1 (cursos vigentes).
        comoAlumno(1L);
        Usuario usuario = alumnoHabilitado(1L);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(usuario));
        Curso curso = cursoPublicadoGratuito();
        curso.setFechaInicio(LocalDate.of(2026, 9, 1));
        Matricula m = new Matricula();
        m.setId(500L);
        m.setCurso(curso);
        m.setUsuario(usuario);
        m.setEstado("ACTIVA");
        m.setFechaMatricula(Instant.parse("2026-09-01T00:00:00Z"));
        when(matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(1L)).thenReturn(List.of(m));

        List<MatriculaRespuesta> resultado = servicio.misCursos();

        assertTrue(resultado.get(0).accesoEfectivo());
        assertEquals("Disponible para continuar.", resultado.get(0).mensajeAcceso());
    }

    @Test
    void misCursosDistingueUnaMatriculaVencidaDeLasVigentes() {
        // HU-021 Escenario 2 (vigencia).
        comoAlumno(1L);
        Usuario usuario = alumnoHabilitado(1L);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(usuario));
        Curso curso = cursoPublicadoGratuito();
        curso.setFechaInicio(LocalDate.of(2026, 1, 1));
        Matricula m = new Matricula();
        m.setId(500L);
        m.setCurso(curso);
        m.setUsuario(usuario);
        m.setEstado("ACTIVA");
        m.setFechaMatricula(Instant.parse("2026-01-01T00:00:00Z"));
        m.setFechaVencimiento(Instant.parse("2026-02-01T00:00:00Z"));
        when(matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(1L)).thenReturn(List.of(m));

        List<MatriculaRespuesta> resultado = servicio.misCursos();

        assertFalse(resultado.get(0).accesoEfectivo());
        assertEquals("El acceso venci\u00f3.", resultado.get(0).mensajeAcceso());
    }

    @Test
    void misCursosAntesDelInicioNoOfreceUnAccesoIncorrecto() {
        comoAlumno(1L);
        Usuario usuario = alumnoHabilitado(1L);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(usuario));
        Curso curso = cursoPublicadoGratuito();
        curso.setFechaInicio(LocalDate.of(2026, 12, 1));
        Matricula m = new Matricula();
        m.setId(500L);
        m.setCurso(curso);
        m.setUsuario(usuario);
        m.setEstado("ACTIVA");
        m.setFechaMatricula(Instant.parse("2026-09-01T00:00:00Z"));
        when(matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(1L)).thenReturn(List.of(m));

        List<MatriculaRespuesta> resultado = servicio.misCursos();

        assertFalse(resultado.get(0).accesoEfectivo());
        assertEquals("El curso a\u00fan no inicia.", resultado.get(0).mensajeAcceso());
    }

    // ---------------------------------------------------------------- HU-041 (filtrado) --

    @Test
    void reporteFiltraPorTextoYPorEstado() {
        // HU-041 Escenario 1 (filtros).
        comoAdministrador(1L);
        Usuario usuario = alumnoHabilitado(1L);
        Curso cursoCoincide = cursoPublicadoGratuito();
        cursoCoincide.setTitulo("Derecho Registral");
        Matricula coincide = new Matricula();
        coincide.setId(1L);
        coincide.setCurso(cursoCoincide);
        coincide.setUsuario(usuario);
        coincide.setEstado("ACTIVA");
        coincide.setFormaIngreso("GRATUITA");
        coincide.setFechaMatricula(Instant.parse("2026-09-01T00:00:00Z"));

        Curso cursoNoCoincide = cursoPublicadoGratuito();
        cursoNoCoincide.setTitulo("Derecho Notarial");
        Matricula noCoincide = new Matricula();
        noCoincide.setId(2L);
        noCoincide.setCurso(cursoNoCoincide);
        noCoincide.setUsuario(usuario);
        noCoincide.setEstado("CANCELADA");
        noCoincide.setFormaIngreso("GRATUITA");
        noCoincide.setFechaMatricula(Instant.parse("2026-09-01T00:00:00Z"));

        when(matriculas.exportarReporte(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                new ReporteMatriculaRespuesta(1L, "Ana Pérez", "ana@example.com", "Derecho Registral", "VIRTUAL",
                        coincide.getFechaMatricula(), null, "ACTIVA", "GRATUITA", "EN_CURSO", "NO_EMITIDO")));

        List<ReporteMatriculaRespuesta> resultado = servicio.reporte("registral", "ACTIVA", null, "", null, null);

        assertEquals(1, resultado.size());
        assertEquals("Derecho Registral", resultado.get(0).curso());
    }

    @Test
    void reportePaginadoDelegaAlRepositorio() {
        comoAdministrador(1L);
        Pageable pageable = PageRequest.of(0, 20);
        ReporteMatriculaRespuesta fila = new ReporteMatriculaRespuesta(1L, "Ana Pérez", "ana@example.com",
                "Curso", "VIRTUAL", Instant.parse("2026-09-01T00:00:00Z"), null, "ACTIVA", "GRATUITA",
                "EN_CURSO", "NO_EMITIDO");
        Page<ReporteMatriculaRespuesta> pagina = new org.springframework.data.domain.PageImpl<>(List.of(fila), pageable, 1);
        when(matriculas.buscarReporte(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(pagina);

        Page<ReporteMatriculaRespuesta> resultado = servicio.reportePaginado("", "", null, "", null, null, pageable);

        assertEquals(1, resultado.getTotalElements());
        assertEquals("Curso", resultado.getContent().getFirst().curso());
    }
}
