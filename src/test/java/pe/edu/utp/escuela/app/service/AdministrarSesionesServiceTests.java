package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.utp.escuela.app.dto.AsistenciaSesionResponse;
import pe.edu.utp.escuela.app.dto.CancelarSesionRequest;
import pe.edu.utp.escuela.app.dto.CorregirAsistenciaRequest;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionRequest;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionResultado;
import pe.edu.utp.escuela.app.entity.Asistencia;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.ExcepcionAcademica;
import pe.edu.utp.escuela.app.entity.HistorialSesion;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.event.ReevaluarCertificacionEvent;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.AsistenciaRepository;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.HistorialSesionRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;

/** EP04 · HU-027 — Administrar cambios en sesiones: reprogramar, cancelar y corregir asistencia con historial. */
@ExtendWith(MockitoExtension.class)
class AdministrarSesionesServiceTests {

    @Mock private LeccionRepository lecciones;
    @Mock private CursoRepository cursos;
    @Mock private MatriculaRepository matriculas;
    @Mock private AsistenciaRepository asistencias;
    @Mock private HistorialSesionRepository historial;
    @Mock private NotificacionRepository notificaciones;
    @Mock private ExcepcionAcademicaRepository excepciones;
    @Mock private MailService mailService;
    @Mock private ProgresoService progresoService;
    @Mock private ApplicationEventPublisher eventos;
    @Mock private CurrentUserService actual;

    private AdministrarSesionesService service;
    private final Clock clock = Clock.fixed(AcademiaFixtures.AHORA, ZoneId.of("America/Lima"));

    private Curso curso;
    private Modulo modulo;
    private Matricula matricula;
    private Leccion sesionFutura;

    @BeforeEach
    void setUp() {
        service = new AdministrarSesionesService(lecciones, cursos, matriculas, asistencias, historial,
                notificaciones, excepciones, mailService, progresoService, eventos, actual, clock);
        curso = AcademiaFixtures.curso(200L, "EN_CURSO", "EN_VIVO");
        modulo = AcademiaFixtures.modulo(1L, curso, 1);
        matricula = AcademiaFixtures.matricula(5L, AcademiaFixtures.alumno(10L), curso, "ACTIVA");
        sesionFutura = AcademiaFixtures.leccion(11L, modulo, 1, "EN_VIVO", true);
        sesionFutura.setFechaHoraInicio(Instant.parse("2026-09-20T15:00:00Z"));
        sesionFutura.setFechaHoraFin(Instant.parse("2026-09-20T17:00:00Z"));
        sesionFutura.setEnlaceReunion("https://meet.example.com/clase");
    }

    private void comoAdministrador() {
        when(actual.get()).thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    @Test
    void soloUnAdministradorPuedeReprogramar() {
        when(actual.get()).thenReturn(new CurrentUser(10L, "ana@example.com", Set.of("ALUMNO")));

        assertThrows(ForbiddenException.class, () -> service.reprogramar(11L, new ReprogramarSesionRequest(
                Instant.parse("2026-09-21T15:00:00Z"), Instant.parse("2026-09-21T17:00:00Z"), "Cruce de horarios")));

        verifyNoInteractions(lecciones, historial);
    }

    @Test
    void reprogramarGuardaElNuevoHorarioConHistorialYAvisaALosAlumnosActivos() {
        comoAdministrador();
        when(lecciones.findById(11L)).thenReturn(Optional.of(sesionFutura));
        when(matriculas.findByEstadoAndCurso_Id("ACTIVA", 200L)).thenReturn(List.of(matricula));
        Instant nuevoInicio = Instant.parse("2026-09-21T15:00:00Z");
        Instant nuevoFin = Instant.parse("2026-09-21T17:00:00Z");

        ReprogramarSesionResultado resultado = service.reprogramar(11L,
                new ReprogramarSesionRequest(nuevoInicio, nuevoFin, "Cruce de horarios"));

        assertTrue(resultado.aplicado());
        assertFalse(resultado.requiereAmpliarFinDeCurso());
        assertEquals(nuevoInicio, sesionFutura.getFechaHoraInicio());
        verify(lecciones).save(sesionFutura);
        ArgumentCaptor<HistorialSesion> entrada = ArgumentCaptor.forClass(HistorialSesion.class);
        verify(historial).save(entrada.capture());
        assertEquals("REPROGRAMACION", entrada.getValue().getAccion());
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), entrada.getValue().getInicioAnterior());
        assertEquals(nuevoInicio, entrada.getValue().getInicioNuevo());
        assertEquals("Cruce de horarios", entrada.getValue().getMotivo());
        ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(aviso.capture());
        assertEquals("ENVIADO", aviso.getValue().getEstadoEnvio());
        assertEquals("ana@example.com", aviso.getValue().getDestinatario());
    }

    @Test
    void reprogramarSinMotivoSeRechazaYNoCambiaNada() {
        comoAdministrador();
        when(lecciones.findById(11L)).thenReturn(Optional.of(sesionFutura));

        BusinessValidationException error = assertThrows(BusinessValidationException.class,
                () -> service.reprogramar(11L, new ReprogramarSesionRequest(
                        Instant.parse("2026-09-21T15:00:00Z"), Instant.parse("2026-09-21T17:00:00Z"), " ")));

        assertEquals("Indica el motivo de la reprogramación.", error.getMessage());
        verify(lecciones, never()).save(any());
        verifyNoInteractions(historial, mailService);
    }

    @Test
    void laHoraDeFinDebeSerPosteriorAlInicio() {
        comoAdministrador();
        when(lecciones.findById(11L)).thenReturn(Optional.of(sesionFutura));
        Instant mismaHora = Instant.parse("2026-09-21T15:00:00Z");

        BusinessValidationException error = assertThrows(BusinessValidationException.class,
                () -> service.reprogramar(11L, new ReprogramarSesionRequest(mismaHora, mismaHora, "Cambio")));

        assertEquals("La hora de fin debe ser posterior al inicio.", error.getMessage());
    }

    @Test
    void siLaNuevaFechaSuperaElFinDelCursoSePideAmpliarloSinGuardarNada() {
        comoAdministrador();
        when(lecciones.findById(11L)).thenReturn(Optional.of(sesionFutura));

        ReprogramarSesionResultado resultado = service.reprogramar(11L, new ReprogramarSesionRequest(
                Instant.parse("2027-01-10T15:00:00Z"), Instant.parse("2027-01-10T17:00:00Z"), "Feriado"));

        assertFalse(resultado.aplicado());
        assertTrue(resultado.requiereAmpliarFinDeCurso());
        assertEquals(LocalDate.of(2027, 1, 10), resultado.fechaFinCursoSugerida());
        verify(lecciones, never()).save(any());
        verifyNoInteractions(historial);
    }

    @Test
    void unaSesionYaRealizadaNoSePuedeModificar() {
        comoAdministrador();
        Leccion realizada = AcademiaFixtures.leccion(12L, modulo, 2, "EN_VIVO", true);
        realizada.setFechaHoraInicio(Instant.parse("2026-09-10T15:00:00Z"));
        realizada.setFechaHoraFin(Instant.parse("2026-09-10T17:00:00Z"));
        when(lecciones.findById(12L)).thenReturn(Optional.of(realizada));

        BusinessValidationException error = assertThrows(BusinessValidationException.class,
                () -> service.cancelar(12L, new CancelarSesionRequest("Error")));

        assertEquals("No puedes modificar una sesión ya realizada.", error.getMessage());
    }

    @Test
    void cancelarMarcaLaSesionConsevaElHistorialYAvisa() {
        comoAdministrador();
        when(lecciones.findById(11L)).thenReturn(Optional.of(sesionFutura));
        when(matriculas.findByEstadoAndCurso_Id("ACTIVA", 200L)).thenReturn(List.of(matricula));

        service.cancelar(11L, new CancelarSesionRequest("Docente enfermo"));

        assertEquals("CANCELADA", sesionFutura.getEstado());
        assertEquals("Docente enfermo", sesionFutura.getMotivoCancelacion());
        ArgumentCaptor<HistorialSesion> entrada = ArgumentCaptor.forClass(HistorialSesion.class);
        verify(historial).save(entrada.capture());
        assertEquals("CANCELACION", entrada.getValue().getAccion());
        verify(mailService).sendHtml(any());
    }

    @Test
    void cancelarDosVecesLaMismaSesionSeRechaza() {
        comoAdministrador();
        sesionFutura.setEstado("CANCELADA");
        when(lecciones.findById(11L)).thenReturn(Optional.of(sesionFutura));

        BusinessValidationException error = assertThrows(BusinessValidationException.class,
                () -> service.cancelar(11L, new CancelarSesionRequest("Otra vez")));

        assertEquals("Esta sesión ya está cancelada.", error.getMessage());
    }

    @Test
    void corregirLaAsistenciaConservaElValorAnteriorYReevaluaLaCertificacion() {
        comoAdministrador();
        Leccion realizada = AcademiaFixtures.leccion(12L, modulo, 2, "EN_VIVO", true);
        Asistencia ausente = new Asistencia();
        ausente.setId(70L);
        ausente.setMatricula(matricula);
        ausente.setLeccion(realizada);
        ausente.setEstado("AUSENTE");
        when(asistencias.findById(70L)).thenReturn(Optional.of(ausente));

        service.corregirAsistencia(70L, new CorregirAsistenciaRequest("JUSTIFICADA", "Presentó certificado médico"));

        assertEquals("JUSTIFICADA", ausente.getEstado());
        assertEquals("Presentó certificado médico", ausente.getMotivoCorreccion());
        assertEquals(1L, ausente.getRegistradaPorUsuarioId());
        ArgumentCaptor<ExcepcionAcademica> excepcion = ArgumentCaptor.forClass(ExcepcionAcademica.class);
        verify(excepciones).save(excepcion.capture());
        assertEquals(ExcepcionAcademica.CORREGIR_ASISTENCIA, excepcion.getValue().getTipo());
        assertEquals("AUSENTE", excepcion.getValue().getValorAnterior());
        assertEquals("JUSTIFICADA", excepcion.getValue().getValorNuevo());
        verify(progresoService).completarPorAsistencia(matricula, realizada, "JUSTIFICADA");
        verify(eventos).publishEvent(new ReevaluarCertificacionEvent(5L));
    }

    @Test
    void corregirAsistenciaExigeMotivoYUnEstadoValido() {
        comoAdministrador();
        Asistencia asistencia = new Asistencia();
        asistencia.setEstado("AUSENTE");
        when(asistencias.findById(70L)).thenReturn(Optional.of(asistencia));

        assertEquals("Indica el motivo de la corrección.", assertThrows(BusinessValidationException.class,
                () -> service.corregirAsistencia(70L, new CorregirAsistenciaRequest("PRESENTE", " "))).getMessage());
        assertEquals("El estado de asistencia no es válido.", assertThrows(BusinessValidationException.class,
                () -> service.corregirAsistencia(70L, new CorregirAsistenciaRequest("TARDE", "Motivo"))).getMessage());

        assertEquals("AUSENTE", asistencia.getEstado());
        verifyNoInteractions(excepciones, eventos);
    }

    @Test
    void listarAsistenciasEntregaElNombreDelAlumnoYSuEstado() {
        comoAdministrador();
        Asistencia presente = new Asistencia();
        presente.setId(70L);
        presente.setMatricula(matricula);
        presente.setEstado("PRESENTE");
        when(asistencias.buscarDeLeccionConAlumno(12L)).thenReturn(List.of(presente));

        List<AsistenciaSesionResponse> filas = service.listarAsistencias(12L);

        assertEquals(1, filas.size());
        assertEquals("Ana Pérez", filas.getFirst().alumnoNombre());
        assertEquals("PRESENTE", filas.getFirst().estado());
    }
}
