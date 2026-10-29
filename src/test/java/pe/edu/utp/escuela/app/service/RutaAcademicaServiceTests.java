package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.IntentoExamen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.ExamenRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.IntentoExamenRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.ModuloRepository;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.EstadoExamen;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/** EP04 · HU-025 — Ruta académica: qué lecciones quedan bloqueadas y por qué, y cuándo un examen puede rendirse. */
@ExtendWith(MockitoExtension.class)
class RutaAcademicaServiceTests {

    @Mock private ModuloRepository modulos;
    @Mock private LeccionRepository lecciones;
    @Mock private ProgresoLeccionRepository progresoLecciones;
    @Mock private ReglaCursoRepository reglasCurso;
    @Mock private ExamenRepository examenes;
    @Mock private IntentoExamenRepository intentos;
    @Mock private ExcepcionAcademicaRepository excepciones;
    @Mock private CertificadoRepository certificados;

    private RutaAcademicaService service;
    private final Clock clock = Clock.fixed(AcademiaFixtures.AHORA, ZoneId.of("America/Lima"));

    private Curso curso;
    private Modulo modulo1;
    private Modulo modulo2;
    private Matricula matricula;
    private Leccion l11;
    private Leccion l12;
    private Leccion l21;

    @BeforeEach
    void setUp() {
        service = new RutaAcademicaService(modulos, lecciones, progresoLecciones, reglasCurso, examenes, intentos,
                excepciones, certificados, clock);
        curso = AcademiaFixtures.curso(200L, "EN_CURSO", "VIRTUAL");
        modulo1 = AcademiaFixtures.modulo(1L, curso, 1);
        modulo2 = AcademiaFixtures.modulo(2L, curso, 2);
        matricula = AcademiaFixtures.matricula(5L, AcademiaFixtures.alumno(10L), curso, "ACTIVA");
        l11 = AcademiaFixtures.leccion(11L, modulo1, 1, "VIDEO", true);
        l12 = AcademiaFixtures.leccion(12L, modulo1, 2, "VIDEO", true);
        l21 = AcademiaFixtures.leccion(21L, modulo2, 1, "VIDEO", true);
        when(modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(200L)).thenReturn(List.of(modulo1, modulo2));
        when(lecciones.buscarActivasDeModulos(any())).thenReturn(List.of(l11, l12, l21));
        when(intentos.findByMatricula_IdOrderByIniciadoEnAsc(5L)).thenReturn(List.of());
        when(excepciones.findByMatricula_IdAndTipo(any(), any())).thenReturn(List.of());
        when(certificados.findByLogro_Matricula_Id(5L)).thenReturn(Optional.empty());
    }

    private void conRegla(ReglaCurso regla) {
        when(reglasCurso.findByCurso_Id(200L)).thenReturn(Optional.of(regla));
    }

    private void completadas(Leccion... hechas) {
        List<ProgresoLeccion> progreso = java.util.Arrays.stream(hechas).map(leccion -> {
            ProgresoLeccion p = new ProgresoLeccion();
            p.setLeccion(leccion);
            p.setCompletada(true);
            return p;
        }).toList();
        when(progresoLecciones.findByMatricula_Id(5L)).thenReturn(progreso);
    }

    private Examen examenDeModulo(Long id, Modulo modulo, boolean bloqueante) {
        Examen examen = AcademiaFixtures.examen(id, curso, modulo, "CALIFICADO", "MODULO");
        examen.setBloqueaSiguienteModulo(bloqueante);
        examen.setMaximoIntentos(2);
        return examen;
    }

    private IntentoExamen intento(Examen examen, int numero, String estado, String nota, boolean aprobado) {
        IntentoExamen intento = new IntentoExamen();
        intento.setMatricula(matricula);
        intento.setExamen(examen);
        intento.setNumeroIntento(numero);
        intento.setEstado(estado);
        intento.setNota(new BigDecimal(nota));
        intento.setAprobado(aprobado);
        return intento;
    }

    @Test
    void conSecuenciaObligatoriaCadaLeccionExigeLaAnteriorCompletada() {
        conRegla(new ReglaCurso());
        when(progresoLecciones.findByMatricula_Id(5L)).thenReturn(List.of());
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of());

        RutaAcademica ruta = service.calcular(matricula);

        assertNull(ruta.motivoBloqueo(11L));
        assertEquals("Completa la lección anterior: Lección 11", ruta.motivoBloqueo(12L));
        assertEquals("Completa la lección anterior: Lección 12", ruta.motivoBloqueo(21L));
        assertEquals(List.of(11L, 12L, 21L), ruta.globalOrden().stream().map(Leccion::getId).toList());
    }

    @Test
    void alCompletarLaLeccionPreviaSeLiberaLaSiguiente() {
        conRegla(new ReglaCurso());
        completadas(l11);
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of());

        RutaAcademica ruta = service.calcular(matricula);

        assertNull(ruta.motivoBloqueo(11L));
        assertNull(ruta.motivoBloqueo(12L));
        assertEquals("Completa la lección anterior: Lección 12", ruta.motivoBloqueo(21L));
        assertTrue(ruta.completadas().get(11L));
    }

    @Test
    void sinSecuenciaObligatoriaNingunaLeccionQuedaBloqueada() {
        ReglaCurso libre = new ReglaCurso();
        libre.setSecuenciaObligatoria(false);
        conRegla(libre);
        when(progresoLecciones.findByMatricula_Id(5L)).thenReturn(List.of());
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of());

        RutaAcademica ruta = service.calcular(matricula);

        assertTrue(ruta.bloqueos().isEmpty());
    }

    @Test
    void unaSesionCanceladaLiberaLaSecuenciaYNoCuentaComoPrevia() {
        conRegla(new ReglaCurso());
        l12.setEstado("CANCELADA");
        completadas(l11);
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of());

        RutaAcademica ruta = service.calcular(matricula);

        // l12 queda sin completar pero cancelada: no frena al primer contenido del módulo siguiente.
        assertNull(ruta.motivoBloqueo(21L));
    }

    @Test
    void unExamenDeModuloBloqueanteSinAprobarFrenaElSiguienteModulo() {
        conRegla(new ReglaCurso());
        completadas(l11, l12);
        Examen examen = examenDeModulo(100L, modulo1, true);
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of(examen));

        RutaAcademica ruta = service.calcular(matricula);

        assertEquals("Aprueba el examen «Examen 100» para continuar.", ruta.motivoBloqueo(21L));
        assertEquals("PENDIENTE", ruta.estadoExamenes().get(100L).codigo());
        assertTrue(ruta.estadoExamenes().get(100L).puedeIniciar());
    }

    @Test
    void aprobarElExamenBloqueanteLiberaElSiguienteModulo() {
        conRegla(new ReglaCurso());
        completadas(l11, l12);
        Examen examen = examenDeModulo(100L, modulo1, true);
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of(examen));
        when(intentos.findByMatricula_IdOrderByIniciadoEnAsc(5L))
                .thenReturn(List.of(intento(examen, 1, IntentoExamen.CALIFICADO, "15.00", true)));

        RutaAcademica ruta = service.calcular(matricula);

        assertNull(ruta.motivoBloqueo(21L));
        assertTrue(ruta.examenesAprobados().contains(100L));
        assertEquals(0, new BigDecimal("15.00").compareTo(ruta.mejoresNotas().get(100L)));
        assertEquals("APROBADO", ruta.estadoExamenes().get(100L).codigo());
    }

    @Test
    void unExamenDeModuloSeHabilitaSoloAlCompletarLasLeccionesDeSuModulo() {
        conRegla(new ReglaCurso());
        completadas(l11);
        Examen examen = examenDeModulo(100L, modulo1, false);
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of(examen));

        EstadoExamen estado = service.calcular(matricula).estadoExamenes().get(100L);

        assertFalse(estado.puedeIniciar());
        assertEquals("Completa las lecciones del módulo «Módulo 1» para rendir este examen.",
                estado.motivoNoPuedeIniciar());
    }

    @Test
    void elExamenFinalExigeTodosLosModulosCompletados() {
        conRegla(new ReglaCurso());
        completadas(l11, l12);
        Examen finalDelCurso = AcademiaFixtures.examen(300L, curso, null, "CALIFICADO", "FINAL");
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of(finalDelCurso));

        RutaAcademica ruta = service.calcular(matricula);

        assertEquals("Completa todos los módulos del curso para rendir el examen final.",
                ruta.estadoExamenes().get(300L).motivoNoPuedeIniciar());
        assertEquals(1, ruta.examenesFinales().size());
        assertFalse(ruta.examenesFinales().getFirst().habilitado());
    }

    @Test
    void agotarLosIntentosSinAprobarImpideReintentarYExigeUnaExcepcionDeAdministracion() {
        conRegla(new ReglaCurso());
        completadas(l11, l12, l21);
        Examen examen = examenDeModulo(100L, modulo1, false);
        examen.setMaximoIntentos(1);
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of(examen));
        when(intentos.findByMatricula_IdOrderByIniciadoEnAsc(5L))
                .thenReturn(List.of(intento(examen, 1, IntentoExamen.CALIFICADO, "8.00", false)));

        EstadoExamen estado = service.calcular(matricula).estadoExamenes().get(100L);

        assertTrue(estado.agotadoSinAprobar());
        assertFalse(estado.puedeIniciar());
        assertEquals(0, estado.restantes());
        assertEquals("NO_APROBADO", estado.codigo());
        assertTrue(estado.motivoNoPuedeIniciar().startsWith("Agotaste tus intentos sin aprobar"));
    }

    @Test
    void laPracticaSiempreEsIlimitadaYNoBloquea() {
        conRegla(new ReglaCurso());
        completadas(l11, l12);
        Examen practica = AcademiaFixtures.examen(101L, curso, modulo1, "PRACTICA", "MODULO");
        practica.setBloqueaSiguienteModulo(true);
        practica.setMaximoIntentos(1);
        when(examenes.findByCurso_IdOrderByOrdenAsc(200L)).thenReturn(List.of(practica));
        when(intentos.findByMatricula_IdOrderByIniciadoEnAsc(5L))
                .thenReturn(List.of(intento(practica, 1, IntentoExamen.CALIFICADO, "5.00", false)));

        RutaAcademica ruta = service.calcular(matricula);

        assertNull(ruta.estadoExamenes().get(101L).permitidos());
        assertTrue(ruta.estadoExamenes().get(101L).puedeIniciar());
        assertNull(ruta.motivoBloqueo(21L));
    }
}
