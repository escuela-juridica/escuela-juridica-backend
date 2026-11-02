package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.dto.IngresarSesionResponse;
import pe.edu.utp.escuela.app.dto.SesionCalendarioResponse;
import pe.edu.utp.escuela.app.entity.Asistencia;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.AsistenciaRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;

/** EP04 · HU-026 — Participar en sesiones en vivo: calendario del alumno, ingreso dentro de la ventana con asistencia
 * automática, cierre de asistencias y porcentaje de asistencia. */
@ExtendWith(MockitoExtension.class)
class SesionAlumnoServiceTests {

    @Mock private MatriculaRepository matriculas;
    @Mock private LeccionRepository lecciones;
    @Mock private AsistenciaRepository asistencias;
    @Mock private MaterialLeccionRepository materiales;
    @Mock private NotificacionRepository notificaciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private MailService mailService;
    @Mock private ProgresoService progresoService;
    @Mock private CurrentUserService actual;

    private SesionAlumnoService service;
    // 2026-09-15 07:00 en Lima.
    private final Clock clock = Clock.fixed(AcademiaFixtures.AHORA, ZoneId.of("America/Lima"));

    private Usuario usuario;
    private Curso curso;
    private Modulo modulo;
    private Matricula matricula;

    @BeforeEach
    void setUp() {
        service = new SesionAlumnoService(matriculas, lecciones, asistencias, materiales, notificaciones, usuarios,
                mailService, progresoService, actual, clock);
        usuario = AcademiaFixtures.alumno(10L);
        curso = AcademiaFixtures.curso(200L, "EN_CURSO", "EN_VIVO");
        modulo = AcademiaFixtures.modulo(1L, curso, 1);
        matricula = AcademiaFixtures.matricula(5L, usuario, curso, "ACTIVA");
    }

    private void comoAlumno() {
        when(actual.get()).thenReturn(new CurrentUser(10L, "ana@example.com", Set.of("ALUMNO")));
        when(usuarios.findWithPersonaById(10L)).thenReturn(Optional.of(usuario));
    }

    private Leccion sesion(Long id, String inicio, String fin) {
        Leccion sesion = AcademiaFixtures.leccion(id, modulo, 1, "EN_VIVO", true);
        sesion.setFechaHoraInicio(Instant.parse(inicio));
        sesion.setFechaHoraFin(Instant.parse(fin));
        sesion.setEnlaceReunion("https://meet.example.com/clase-" + id);
        return sesion;
    }

    // ---------------------------------------------------------------- ingresar --

    @Test
    void ingresarDentroDeLaVentanaRegistraAsistenciaPresenteYEntregaElEnlace() {
        comoAlumno();
        Leccion enCurso = sesion(11L, "2026-09-15T11:00:00Z", "2026-09-15T13:00:00Z");
        when(lecciones.findById(11L)).thenReturn(Optional.of(enCurso));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(matricula));
        when(asistencias.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        IngresarSesionResponse respuesta = service.ingresar(11L);

        assertEquals("https://meet.example.com/clase-11", respuesta.enlaceReunion());
        ArgumentCaptor<Asistencia> guardada = ArgumentCaptor.forClass(Asistencia.class);
        verify(asistencias).save(guardada.capture());
        assertEquals("PRESENTE", guardada.getValue().getEstado());
        assertEquals(matricula, guardada.getValue().getMatricula());
        assertEquals(AcademiaFixtures.AHORA, guardada.getValue().getRegistradaEn());
    }

    @Test
    void ingresarDosVecesNoDuplicaLaAsistencia() {
        comoAlumno();
        Leccion enCurso = sesion(11L, "2026-09-15T11:00:00Z", "2026-09-15T13:00:00Z");
        when(lecciones.findById(11L)).thenReturn(Optional.of(enCurso));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(matricula));
        when(asistencias.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.of(new Asistencia()));

        service.ingresar(11L);

        verify(asistencias, never()).save(any());
    }

    @Test
    void antesDeLaHoraDeInicioNoSePuedeIngresar() {
        comoAlumno();
        Leccion futura = sesion(11L, "2026-09-16T15:00:00Z", "2026-09-16T17:00:00Z");
        when(lecciones.findById(11L)).thenReturn(Optional.of(futura));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(matricula));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.ingresar(11L));

        assertEquals("La sesión solo está disponible entre la hora de inicio y la hora de fin.", error.getMessage());
        verify(asistencias, never()).save(any());
    }

    @Test
    void unaSesionCanceladaNoAdmiteIngreso() {
        comoAlumno();
        Leccion cancelada = sesion(11L, "2026-09-15T11:00:00Z", "2026-09-15T13:00:00Z");
        cancelada.setEstado("CANCELADA");
        when(lecciones.findById(11L)).thenReturn(Optional.of(cancelada));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.ingresar(11L));

        assertEquals("La sesión fue cancelada.", error.getMessage());
    }

    @Test
    void sinMatriculaNoSePuedeIngresarALaSesion() {
        comoAlumno();
        Leccion enCurso = sesion(11L, "2026-09-15T11:00:00Z", "2026-09-15T13:00:00Z");
        when(lecciones.findById(11L)).thenReturn(Optional.of(enCurso));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.empty());

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.ingresar(11L));

        assertEquals("No tienes una matrícula en este curso.", error.getMessage());
        verify(asistencias, never()).save(any());
    }

    @Test
    void soloUnAlumnoIngresaALaSesion() {
        when(actual.get()).thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));

        assertThrows(ForbiddenException.class, () -> service.ingresar(11L));
    }

    // ---------------------------------------------------------------- calendario --

    @Test
    void elCalendarioRechazaUnRangoInvertido() {
        comoAlumno();

        BusinessValidationException error = assertThrows(BusinessValidationException.class,
                () -> service.calendario(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)));

        assertEquals("La fecha final no puede ser anterior a la fecha inicial.", error.getMessage());
    }

    @Test
    void sinMatriculasActivasElCalendarioVieneVacio() {
        comoAlumno();
        Matricula pendiente = AcademiaFixtures.matricula(6L, usuario, curso, "PENDIENTE_PAGO");
        when(matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(10L)).thenReturn(List.of(pendiente));

        assertEquals(List.of(), service.calendario(null, null));

        verify(lecciones, never()).buscarSesionesEnVivoDeCursosEntre(any(), any(), any());
    }

    @Test
    void elCalendarioMuestraElEnlaceSoloDuranteLaVentanaDeLaSesion() {
        comoAlumno();
        when(matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(10L)).thenReturn(List.of(matricula));
        Leccion enVentana = sesion(11L, "2026-09-15T11:00:00Z", "2026-09-15T13:00:00Z");
        Leccion futura = sesion(12L, "2026-09-18T15:00:00Z", "2026-09-18T17:00:00Z");
        Leccion pasada = sesion(13L, "2026-09-10T15:00:00Z", "2026-09-10T17:00:00Z");
        when(lecciones.buscarSesionesEnVivoDeCursosEntre(eq(List.of(200L)), any(), any()))
                .thenReturn(List.of(pasada, enVentana, futura));
        when(materiales.buscarLeccionesConMaterialActivo(any())).thenReturn(List.of(13L));

        List<SesionCalendarioResponse> calendario =
                service.calendario(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertEquals(List.of("REALIZADA", "EN_VENTANA", "FUTURA"),
                calendario.stream().map(SesionCalendarioResponse::estadoSesion).toList());
        assertEquals("https://meet.example.com/clase-11", calendario.get(1).enlaceReunion());
        assertNull(calendario.get(2).enlaceReunion());
        assertEquals(List.of(true, false, false),
                calendario.stream().map(SesionCalendarioResponse::grabacionDisponible).toList());
        assertEquals("Derecho Procesal Penal", calendario.getFirst().cursoTitulo());
    }

    // ---------------------------------------------------------------- jobs y porcentaje --

    @Test
    void alTerminarLaSesionQuienNoIngresoQuedaAusenteYNoCompletaLaLeccion() {
        Leccion terminada = sesion(11L, "2026-09-10T15:00:00Z", "2026-09-10T17:00:00Z");
        when(lecciones.buscarSesionesFinalizadasNoCanceladas(AcademiaFixtures.AHORA)).thenReturn(List.of(terminada));
        when(matriculas.findByEstadoAndCurso_Id("ACTIVA", 200L)).thenReturn(List.of(matricula));
        when(asistencias.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        service.cerrarAsistenciasPendientesProgramado();

        ArgumentCaptor<Asistencia> guardada = ArgumentCaptor.forClass(Asistencia.class);
        verify(asistencias).save(guardada.capture());
        assertEquals("AUSENTE", guardada.getValue().getEstado());
        verify(progresoService).completarPorAsistencia(matricula, terminada, "AUSENTE");
    }

    @Test
    void unaSesionAnteriorALaMatriculaNoGeneraAusencias() {
        matricula.setFechaMatricula(Instant.parse("2026-09-12T00:00:00Z"));
        Leccion previa = sesion(11L, "2026-09-10T15:00:00Z", "2026-09-10T17:00:00Z");
        when(lecciones.buscarSesionesFinalizadasNoCanceladas(AcademiaFixtures.AHORA)).thenReturn(List.of(previa));
        when(matriculas.findByEstadoAndCurso_Id("ACTIVA", 200L)).thenReturn(List.of(matricula));

        service.cerrarAsistenciasPendientesProgramado();

        verify(asistencias, never()).save(any());
    }

    @Test
    void elPorcentajeDeAsistenciaCuentaPresentesYJustificadasSobreLasSesionesElegibles() {
        Leccion primera = sesion(11L, "2026-09-10T15:00:00Z", "2026-09-10T17:00:00Z");
        Leccion segunda = sesion(12L, "2026-09-11T15:00:00Z", "2026-09-11T17:00:00Z");
        Leccion cancelada = sesion(13L, "2026-09-12T15:00:00Z", "2026-09-12T17:00:00Z");
        cancelada.setEstado("CANCELADA");
        when(matriculas.findById(5L)).thenReturn(Optional.of(matricula));
        when(lecciones.buscarSesionesEnVivoDeCurso(200L)).thenReturn(List.of(primera, segunda, cancelada));
        Asistencia presente = new Asistencia();
        presente.setLeccion(primera);
        presente.setEstado("PRESENTE");
        Asistencia ausente = new Asistencia();
        ausente.setLeccion(segunda);
        ausente.setEstado("AUSENTE");
        when(asistencias.findByMatricula_Id(5L)).thenReturn(List.of(presente, ausente));

        assertEquals(50, service.porcentajeAsistencia(5L));
    }

    @Test
    void sinSesionesElegiblesLaAsistenciaNoAplica() {
        when(matriculas.findById(5L)).thenReturn(Optional.of(matricula));
        when(lecciones.buscarSesionesEnVivoDeCurso(200L)).thenReturn(List.of());

        assertNull(service.porcentajeAsistencia(5L));
    }

    @Test
    void elRecordatorioDelDiaAnteriorSeEnviaUnaSolaVezPorSesionYAlumno() {
        Leccion manana = sesion(11L, "2026-09-16T15:00:00Z", "2026-09-16T17:00:00Z");
        when(lecciones.buscarSesionesEnVivoQueInicianEntre(any(), any())).thenReturn(List.of(manana));
        when(matriculas.findByEstadoAndCurso_Id("ACTIVA", 200L)).thenReturn(List.of(matricula));
        when(notificaciones.findTopByTipoOrderByCreadoEnDesc("RECORDATORIO_SESION_11_5")).thenReturn(Optional.empty());

        service.enviarRecordatoriosSesionProgramado();

        ArgumentCaptor<HtmlMailMessage> correo = ArgumentCaptor.forClass(HtmlMailMessage.class);
        verify(mailService).sendHtml(correo.capture());
        assertEquals(List.of("ana@example.com"), correo.getValue().recipients());
        ArgumentCaptor<Notificacion> notificacion = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(notificacion.capture());
        assertEquals("ENVIADO", notificacion.getValue().getEstadoEnvio());

        Notificacion yaEnviada = new Notificacion();
        yaEnviada.setEstadoEnvio("ENVIADO");
        when(notificaciones.findTopByTipoOrderByCreadoEnDesc("RECORDATORIO_SESION_11_5"))
                .thenReturn(Optional.of(yaEnviada));
        service.enviarRecordatoriosSesionProgramado();
        verify(mailService).sendHtml(any());
    }
}
