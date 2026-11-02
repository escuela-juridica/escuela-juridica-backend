package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.dto.AulaResponse;
import pe.edu.utp.escuela.app.dto.LeccionAulaResponse;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/** EP04 · HU-022 — Ingresar al aula: autorización por matrícula, temario con módulos y lecciones, estados de
 * bloqueo y punto de continuación. */
@ExtendWith(MockitoExtension.class)
class AulaServiceTests {

    @Mock private MatriculaRepository matriculas;
    @Mock private MaterialLeccionRepository materiales;
    @Mock private RutaAcademicaService rutaAcademica;
    @Mock private AvanceAcademicoService avanceAcademico;
    @Mock private CurrentUserService actual;

    private AulaService service;
    private final Clock clock = Clock.fixed(AcademiaFixtures.AHORA, ZoneId.of("America/Lima"));

    private Usuario usuario;
    private Curso curso;
    private Modulo modulo;

    @BeforeEach
    void setUp() {
        service = new AulaService(matriculas, materiales, rutaAcademica, avanceAcademico, actual, clock);
        usuario = AcademiaFixtures.alumno(10L);
        curso = AcademiaFixtures.curso(200L, "EN_CURSO", "VIRTUAL");
        modulo = AcademiaFixtures.modulo(1L, curso, 1);
    }

    private void comoAlumno() {
        when(actual.get()).thenReturn(new CurrentUser(10L, "ana@example.com", Set.of("ALUMNO")));
    }

    private AvanceAcademico avance(RutaAcademica ruta, int porcentaje) {
        return new AvanceAcademico(ruta, porcentaje, List.of(), false, "Te falta completar lecciones.", 0, 0, null,
                null, false, List.of(), false);
    }

    @Test
    void unAdministradorNoEntraAlAulaDeUnAlumno() {
        when(actual.get()).thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));

        assertThrows(ForbiddenException.class, () -> service.temario(200L));

        verifyNoInteractions(matriculas, rutaAcademica);
    }

    @Test
    void sinMatriculaEnElCursoNoHayAula() {
        comoAlumno();
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.empty());

        ResourceNotFoundException error =
                assertThrows(ResourceNotFoundException.class, () -> service.temario(200L));

        assertEquals("No tienes una matrícula en este curso.", error.getMessage());
        verifyNoInteractions(rutaAcademica);
    }

    @Test
    void matriculaPendienteDePagoNoConcedeAcceso() {
        comoAlumno();
        Matricula pendiente = AcademiaFixtures.matricula(5L, usuario, curso, "PENDIENTE_PAGO");
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(pendiente));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.temario(200L));

        assertEquals("El acceso no está disponible.", error.getMessage());
        verifyNoInteractions(rutaAcademica);
    }

    @Test
    void matriculaCanceladaInformaElMotivo() {
        comoAlumno();
        Matricula cancelada = AcademiaFixtures.matricula(5L, usuario, curso, "CANCELADA");
        cancelada.setMotivoCancelacion("Pago observado");
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(cancelada));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.temario(200L));

        assertEquals("Tu matrícula fue cancelada: Pago observado", error.getMessage());
    }

    @Test
    void cuentaSinCorreoVerificadoDebeHabilitarseAntesDeEntrar() {
        comoAlumno();
        usuario.setCorreoVerificadoEn(null);
        Matricula activa = AcademiaFixtures.matricula(5L, usuario, curso, "ACTIVA");
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(activa));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.temario(200L));

        assertEquals("Completa la habilitación de tu cuenta para continuar.", error.getMessage());
    }

    @Test
    void matriculaVencidaNoPermiteEntrar() {
        comoAlumno();
        Matricula vencida = AcademiaFixtures.matricula(5L, usuario, curso, "ACTIVA");
        vencida.setFechaVencimiento(Instant.parse("2026-09-01T00:00:00Z"));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(vencida));

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.temario(200L));

        assertEquals("El acceso venció.", error.getMessage());
    }

    @Test
    void matriculaActivaRecibeTemarioConEstadosYPuntoDeContinuacion() {
        comoAlumno();
        Matricula activa = AcademiaFixtures.matricula(5L, usuario, curso, "ACTIVA");
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(activa));
        Leccion primera = AcademiaFixtures.leccion(11L, modulo, 1, "VIDEO", true);
        Leccion segunda = AcademiaFixtures.leccion(12L, modulo, 2, "VIDEO", true);
        Leccion tercera = AcademiaFixtures.leccion(13L, modulo, 3, "VIDEO", true);
        RutaAcademica ruta = AcademiaFixtures.ruta(new ReglaCurso(), List.of(modulo), List.of(primera, segunda, tercera),
                Map.of(11L, true, 12L, false, 13L, false),
                Map.of(13L, "Completa la lección anterior: Lección 12"),
                List.of(), Map.of(), Set.of());
        when(rutaAcademica.calcular(activa)).thenReturn(ruta);
        when(avanceAcademico.calcular(activa, ruta)).thenReturn(avance(ruta, 33));

        AulaResponse aula = service.temario(200L);

        assertEquals(5L, aula.matriculaId());
        assertEquals(200L, aula.cursoId());
        assertEquals(33, aula.porcentaje());
        assertFalse(aula.cursoCancelado());
        assertEquals(1, aula.modulos().size());
        List<LeccionAulaResponse> lecciones = aula.modulos().getFirst().lecciones();
        assertEquals(List.of("COMPLETADA", "DISPONIBLE", "BLOQUEADA"),
                lecciones.stream().map(LeccionAulaResponse::estado).toList());
        assertNull(lecciones.get(1).motivoBloqueo());
        assertEquals("Completa la lección anterior: Lección 12", lecciones.get(2).motivoBloqueo());
        // "Continuar el curso" retoma la primera lección disponible, no la completada ni la bloqueada.
        assertEquals(12L, aula.ultimaLeccionIdValida());
    }

    @Test
    void conTodoCompletadoElAulaReabreLaUltimaLeccion() {
        comoAlumno();
        Matricula activa = AcademiaFixtures.matricula(5L, usuario, curso, "ACTIVA");
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(activa));
        Leccion primera = AcademiaFixtures.leccion(11L, modulo, 1, "VIDEO", true);
        Leccion segunda = AcademiaFixtures.leccion(12L, modulo, 2, "VIDEO", true);
        RutaAcademica ruta = AcademiaFixtures.ruta(new ReglaCurso(), List.of(modulo), List.of(primera, segunda),
                Map.of(11L, true, 12L, true), Map.of(), List.of(), Map.of(), Set.of());
        when(rutaAcademica.calcular(activa)).thenReturn(ruta);
        when(avanceAcademico.calcular(activa, ruta)).thenReturn(avance(ruta, 100));

        AulaResponse aula = service.temario(200L);

        assertEquals(100, aula.porcentaje());
        assertEquals(12L, aula.ultimaLeccionIdValida());
    }

    @Test
    void sesionEnVivoRealizadaConGrabacionActivaExponeLaGrabacion() {
        comoAlumno();
        Matricula activa = AcademiaFixtures.matricula(5L, usuario, curso, "ACTIVA");
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(activa));
        Leccion vivo = AcademiaFixtures.leccion(21L, modulo, 1, "EN_VIVO", true);
        vivo.setFechaHoraInicio(Instant.parse("2026-09-10T15:00:00Z"));
        vivo.setFechaHoraFin(Instant.parse("2026-09-10T17:00:00Z"));
        Leccion futura = AcademiaFixtures.leccion(22L, modulo, 2, "EN_VIVO", true);
        futura.setFechaHoraInicio(Instant.parse("2026-09-20T15:00:00Z"));
        futura.setFechaHoraFin(Instant.parse("2026-09-20T17:00:00Z"));
        RutaAcademica ruta = AcademiaFixtures.ruta(new ReglaCurso(), List.of(modulo), List.of(vivo, futura),
                Map.of(21L, false, 22L, false), Map.of(), List.of(), Map.of(), Set.of());
        when(rutaAcademica.calcular(activa)).thenReturn(ruta);
        when(avanceAcademico.calcular(activa, ruta)).thenReturn(avance(ruta, 0));
        MaterialLeccion grabacion = AcademiaFixtures.material(
                31L, vivo, AcademiaFixtures.recurso(41L, "VIDEO", "SUBIDO"), false);
        when(materiales.findByLeccion_IdInOrderByLeccion_IdAscOrdenAsc(any())).thenReturn(List.of(grabacion));

        AulaResponse aula = service.temario(200L);

        List<LeccionAulaResponse> lecciones = aula.modulos().getFirst().lecciones();
        assertEquals("REALIZADA", lecciones.get(0).estadoSesion());
        assertTrue(lecciones.get(0).grabacionDisponible());
        assertEquals("FUTURA", lecciones.get(1).estadoSesion());
        assertFalse(lecciones.get(1).grabacionDisponible());
    }

    @Test
    void cursoCanceladoConservaLaConsultaDeQuienYaTeniaAcceso() {
        comoAlumno();
        Curso cancelado = AcademiaFixtures.curso(200L, "CANCELADO", "VIRTUAL");
        cancelado.setCanceladoEn(Instant.parse("2026-09-10T00:00:00Z"));
        cancelado.setMotivoCancelacion("Cupo insuficiente");
        Modulo moduloCancelado = AcademiaFixtures.modulo(1L, cancelado, 1);
        Matricula activa = AcademiaFixtures.matricula(5L, usuario, cancelado, "ACTIVA");
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.of(activa));
        Leccion unica = AcademiaFixtures.leccion(11L, moduloCancelado, 1, "VIDEO", true);
        RutaAcademica ruta = AcademiaFixtures.ruta(new ReglaCurso(), List.of(moduloCancelado), List.of(unica),
                Map.of(11L, false), Map.of(), List.of(), Map.of(), Set.of());
        when(rutaAcademica.calcular(activa)).thenReturn(ruta);
        when(avanceAcademico.calcular(activa, ruta)).thenReturn(avance(ruta, 0));

        AulaResponse aula = service.temario(200L);

        assertTrue(aula.cursoCancelado());
        assertEquals("Cupo insuficiente", aula.motivoCancelacionCurso());
    }
}
