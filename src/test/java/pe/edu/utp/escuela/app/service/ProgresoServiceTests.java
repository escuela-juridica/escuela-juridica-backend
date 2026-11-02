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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.utp.escuela.app.entity.Asistencia;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.entity.ProgresoMaterial;
import pe.edu.utp.escuela.app.entity.Recurso;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.event.ReevaluarCertificacionEvent;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.AsistenciaRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepository;
import pe.edu.utp.escuela.app.repository.ProgresoMaterialRepository;
import pe.edu.utp.escuela.app.repository.RecursoRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/** EP04 · HU-024 — Completar lecciones y registrar progreso: umbral de video, check manual, asistencia y la regla de
 * que un check completado nunca se desmarca. */
@ExtendWith(MockitoExtension.class)
class ProgresoServiceTests {

    @Mock private MatriculaRepository matriculas;
    @Mock private LeccionRepository lecciones;
    @Mock private MaterialLeccionRepository materiales;
    @Mock private ProgresoLeccionRepository progresoLecciones;
    @Mock private ProgresoMaterialRepository progresoMateriales;
    @Mock private ReglaCursoRepository reglasCurso;
    @Mock private RecursoRepository recursos;
    @Mock private AsistenciaRepository asistencias;
    @Mock private RutaAcademicaService rutaAcademica;
    @Mock private ApplicationEventPublisher eventos;
    @Mock private CurrentUserService actual;

    private ProgresoService service;
    private final Clock clock = Clock.fixed(AcademiaFixtures.AHORA, ZoneId.of("America/Lima"));

    private Usuario usuario;
    private Curso curso;
    private Modulo modulo;
    private Matricula matricula;

    @BeforeEach
    void setUp() {
        service = new ProgresoService(matriculas, lecciones, materiales, progresoLecciones, progresoMateriales,
                reglasCurso, recursos, asistencias, rutaAcademica, eventos, actual, clock);
        usuario = AcademiaFixtures.alumno(10L);
        curso = AcademiaFixtures.curso(200L, "EN_CURSO", "VIRTUAL");
        modulo = AcademiaFixtures.modulo(1L, curso, 1);
        matricula = AcademiaFixtures.matricula(5L, usuario, curso, "ACTIVA");
    }

    private void comoAlumno() {
        when(actual.get()).thenReturn(new CurrentUser(10L, "ana@example.com", Set.of("ALUMNO")));
    }

    private void conMatriculaYRutaLibre() {
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(matricula));
        RutaAcademica libre = AcademiaFixtures.ruta(new ReglaCurso(), List.of(modulo), List.of(),
                Map.of(), Map.of(), List.of(), Map.of(), Set.of());
        when(rutaAcademica.calcular(matricula)).thenReturn(libre);
    }

    private Leccion leccionDeTexto() {
        Leccion leccion = AcademiaFixtures.leccion(11L, modulo, 1, "LECTURA", true);
        when(lecciones.findById(11L)).thenReturn(Optional.of(leccion));
        return leccion;
    }

    // ---------------------------------------------------------------- check manual --

    @Test
    void completarManualRegistraLaLeccionYPideReevaluarLaCertificacion() {
        comoAlumno();
        Leccion leccion = leccionDeTexto();
        conMatriculaYRutaLibre();
        when(materiales.findByLeccion_IdOrderByOrdenAsc(11L)).thenReturn(List.of());
        when(progresoLecciones.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        service.completarManual(11L);

        ArgumentCaptor<ProgresoLeccion> guardado = ArgumentCaptor.forClass(ProgresoLeccion.class);
        verify(progresoLecciones).save(guardado.capture());
        assertTrue(guardado.getValue().isCompletada());
        assertEquals("MANUAL", guardado.getValue().getFormaCompletado());
        assertEquals(leccion, guardado.getValue().getLeccion());
        assertEquals(AcademiaFixtures.AHORA, guardado.getValue().getCompletadaEn());
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertEquals(new ReevaluarCertificacionEvent(5L), evento.getValue());
    }

    @Test
    void unCheckYaCompletadoNoSeTocaNiSeVuelveAReevaluar() {
        comoAlumno();
        leccionDeTexto();
        conMatriculaYRutaLibre();
        when(materiales.findByLeccion_IdOrderByOrdenAsc(11L)).thenReturn(List.of());
        ProgresoLeccion previo = new ProgresoLeccion();
        previo.setId(99L);
        previo.setCompletada(true);
        previo.setFormaCompletado("AUTOMATICO");
        when(progresoLecciones.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.of(previo));

        service.completarManual(11L);

        assertEquals("AUTOMATICO", previo.getFormaCompletado());
        verify(progresoLecciones, never()).save(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void unaLeccionConVideoSubidoSoloSeCompletaViendoElVideo() {
        comoAlumno();
        Leccion leccion = leccionDeTexto();
        conMatriculaYRutaLibre();
        MaterialLeccion video = AcademiaFixtures.material(
                31L, leccion, AcademiaFixtures.recurso(41L, "VIDEO", "SUBIDO"), false);
        when(materiales.findByLeccion_IdOrderByOrdenAsc(11L)).thenReturn(List.of(video));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.completarManual(11L));

        assertEquals("Esta lección se completa automáticamente al ver el video.", error.getMessage());
        verify(progresoLecciones, never()).save(any());
    }

    @Test
    void noSePuedeCompletarUnaLeccionBloqueadaLlamandoDirectoALaApi() {
        comoAlumno();
        leccionDeTexto();
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(matricula));
        RutaAcademica conBloqueo = AcademiaFixtures.ruta(new ReglaCurso(), List.of(modulo), List.of(),
                Map.of(), Map.of(11L, "Completa la lección anterior: Lección 10"), List.of(), Map.of(), Set.of());
        when(rutaAcademica.calcular(matricula)).thenReturn(conBloqueo);

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.completarManual(11L));

        assertEquals("Completa la lección anterior: Lección 10", error.getMessage());
        verify(progresoLecciones, never()).save(any());
    }

    @Test
    void sinMatriculaNoSeRegistraProgreso() {
        comoAlumno();
        leccionDeTexto();
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.completarManual(11L));

        verifyNoInteractions(progresoLecciones, eventos);
    }

    @Test
    void soloElAlumnoRegistraProgreso() {
        when(actual.get()).thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));

        assertThrows(ForbiddenException.class, () -> service.completarManual(11L));

        verifyNoInteractions(lecciones, progresoLecciones, eventos);
    }

    @Test
    void enUnCursoCanceladoLaConsultaSigueAbiertaPeroElProgresoYaNoSeRegistra() {
        comoAlumno();
        Curso cancelado = AcademiaFixtures.curso(200L, "CANCELADO", "VIRTUAL");
        Modulo moduloCancelado = AcademiaFixtures.modulo(1L, cancelado, 1);
        Leccion leccion = AcademiaFixtures.leccion(11L, moduloCancelado, 1, "LECTURA", true);
        when(lecciones.findById(11L)).thenReturn(Optional.of(leccion));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L))
                .thenReturn(Optional.of(AcademiaFixtures.matricula(5L, usuario, cancelado, "ACTIVA")));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.completarManual(11L));

        assertTrue(error.getMessage().contains("ya no se registra progreso"));
        verifyNoInteractions(progresoLecciones, eventos);
    }

    // ---------------------------------------------------------------- video --

    private MaterialLeccion videoSubido(Integer duracionSegundos) {
        Leccion leccion = AcademiaFixtures.leccion(11L, modulo, 1, "VIDEO", true);
        Recurso recurso = AcademiaFixtures.recurso(41L, "VIDEO", "SUBIDO");
        recurso.setDuracionSegundos(duracionSegundos);
        recurso.setDuracionDetectada(duracionSegundos != null);
        MaterialLeccion material = AcademiaFixtures.material(31L, leccion, recurso, false);
        when(materiales.findById(31L)).thenReturn(Optional.of(material));
        when(materiales.findByLeccion_IdOrderByOrdenAsc(11L)).thenReturn(List.of(material));
        return material;
    }

    @Test
    void alcanzarElUmbralDeVideoCompletaLaLeccionAutomaticamente() {
        comoAlumno();
        MaterialLeccion video = videoSubido(600);
        conMatriculaYRutaLibre();
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_Id(5L, 31L)).thenReturn(Optional.empty());
        when(reglasCurso.findByCurso_Id(200L)).thenReturn(Optional.empty());
        ProgresoMaterial guardadoPorElServicio = new ProgresoMaterial();
        guardadoPorElServicio.setMaterialLeccion(video);
        guardadoPorElServicio.setPorcentajeVideo(BigDecimal.valueOf(50));
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_IdIn(5L, List.of(31L)))
                .thenReturn(List.of(guardadoPorElServicio));
        when(progresoLecciones.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        service.registrarProgresoVideo(31L, 300, 300, null);

        ArgumentCaptor<ProgresoMaterial> material = ArgumentCaptor.forClass(ProgresoMaterial.class);
        verify(progresoMateriales).save(material.capture());
        assertEquals(0, BigDecimal.valueOf(50).compareTo(material.getValue().getPorcentajeVideo()));
        assertEquals(300, material.getValue().getUltimaPosicionSegundos());
        ArgumentCaptor<ProgresoLeccion> leccion = ArgumentCaptor.forClass(ProgresoLeccion.class);
        verify(progresoLecciones).save(leccion.capture());
        assertTrue(leccion.getValue().isCompletada());
        assertEquals("AUTOMATICO", leccion.getValue().getFormaCompletado());
        verify(eventos).publishEvent(new ReevaluarCertificacionEvent(5L));
    }

    @Test
    void bajoElUmbralSoloSeActualizaLaVisitaYLaLeccionSigueIncompleta() {
        comoAlumno();
        MaterialLeccion video = videoSubido(600);
        conMatriculaYRutaLibre();
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_Id(5L, 31L)).thenReturn(Optional.empty());
        when(reglasCurso.findByCurso_Id(200L)).thenReturn(Optional.empty());
        ProgresoMaterial reportado = new ProgresoMaterial();
        reportado.setMaterialLeccion(video);
        reportado.setPorcentajeVideo(BigDecimal.valueOf(10));
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_IdIn(5L, List.of(31L)))
                .thenReturn(List.of(reportado));
        when(progresoLecciones.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        service.registrarProgresoVideo(31L, 60, 60, null);

        ArgumentCaptor<ProgresoLeccion> leccion = ArgumentCaptor.forClass(ProgresoLeccion.class);
        verify(progresoLecciones).save(leccion.capture());
        assertFalse(leccion.getValue().isCompletada());
        assertEquals(AcademiaFixtures.AHORA, leccion.getValue().getUltimaVisitaEn());
        verifyNoInteractions(eventos);
    }

    @Test
    void elPorcentajeDeVideoNuncaRetrocedeSiElAlumnoRebobina() {
        comoAlumno();
        MaterialLeccion video = videoSubido(600);
        conMatriculaYRutaLibre();
        ProgresoMaterial previo = new ProgresoMaterial();
        previo.setMatricula(matricula);
        previo.setMaterialLeccion(video);
        previo.setPorcentajeVideo(BigDecimal.valueOf(40));
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_Id(5L, 31L)).thenReturn(Optional.of(previo));
        when(reglasCurso.findByCurso_Id(200L)).thenReturn(Optional.empty());
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_IdIn(5L, List.of(31L)))
                .thenReturn(List.of(previo));
        when(progresoLecciones.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        service.registrarProgresoVideo(31L, 60, 60, null);

        assertEquals(0, BigDecimal.valueOf(40).compareTo(previo.getPorcentajeVideo()));
        assertEquals(60, previo.getUltimaPosicionSegundos());
    }

    @Test
    void laPrimeraDuracionReportadaPorElNavegadorSeAdoptaComoOficial() {
        comoAlumno();
        MaterialLeccion video = videoSubido(null);
        conMatriculaYRutaLibre();
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_Id(5L, 31L)).thenReturn(Optional.empty());
        when(reglasCurso.findByCurso_Id(200L)).thenReturn(Optional.empty());
        when(progresoMateriales.findByMatricula_IdAndMaterialLeccion_IdIn(5L, List.of(31L))).thenReturn(List.of());
        when(progresoLecciones.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        service.registrarProgresoVideo(31L, 30, 30, 900);

        assertEquals(900, video.getRecurso().getDuracionSegundos());
        assertTrue(video.getRecurso().isDuracionDetectada());
        verify(recursos).save(video.getRecurso());
    }

    @Test
    void unEnlaceExternoNoPermiteRegistrarProgresoDeVideo() {
        comoAlumno();
        Leccion leccion = AcademiaFixtures.leccion(11L, modulo, 1, "VIDEO", true);
        MaterialLeccion youtube = AcademiaFixtures.material(
                32L, leccion, AcademiaFixtures.recurso(42L, "VIDEO", "YOUTUBE"), false);
        when(materiales.findById(32L)).thenReturn(Optional.of(youtube));
        conMatriculaYRutaLibre();

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.registrarProgresoVideo(32L, 10, 10, null));

        assertEquals("Este material no permite registrar progreso de video.", error.getMessage());
        verifyNoInteractions(progresoMateriales, eventos);
    }

    // ---------------------------------------------------------------- asistencia --

    @Test
    void laAsistenciaPresenteCompletaLaSesionPeroLaAusenciaNo() {
        Leccion sesion = AcademiaFixtures.leccion(11L, modulo, 1, "EN_VIVO", true);
        when(progresoLecciones.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.empty());

        service.completarPorAsistencia(matricula, sesion, "AUSENTE");
        verify(progresoLecciones, never()).save(any());

        service.completarPorAsistencia(matricula, sesion, "PRESENTE");
        ArgumentCaptor<ProgresoLeccion> guardado = ArgumentCaptor.forClass(ProgresoLeccion.class);
        verify(progresoLecciones).save(guardado.capture());
        assertEquals("ASISTENCIA", guardado.getValue().getFormaCompletado());
    }

    @Test
    void unaSesionEnVivoSinAsistenciaNiGrabacionNoSePuedeCompletarAMano() {
        comoAlumno();
        Leccion sesion = AcademiaFixtures.leccion(11L, modulo, 1, "EN_VIVO", true);
        when(lecciones.findById(11L)).thenReturn(Optional.of(sesion));
        conMatriculaYRutaLibre();
        when(materiales.findByLeccion_IdOrderByOrdenAsc(11L)).thenReturn(List.of());
        Asistencia ausente = new Asistencia();
        ausente.setEstado("AUSENTE");
        when(asistencias.findByMatricula_IdAndLeccion_Id(5L, 11L)).thenReturn(Optional.of(ausente));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.completarManual(11L));

        assertEquals("La grabación todavía no está disponible.", error.getMessage());
        verify(progresoLecciones, never()).save(any());
    }
}
