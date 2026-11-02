package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.escuela.app.dto.CondicionAvanceResponse;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.IntentoExamen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.EstadoExamen;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/** EP05 · HU-032 — Condiciones de certificación: progreso, exámenes, nota promedio y asistencia, y qué le falta al
 * alumno para obtener su certificado. */
@ExtendWith(MockitoExtension.class)
class AvanceAcademicoServiceTests {

    @Mock private RutaAcademicaService rutaAcademica;
    @Mock private SesionAlumnoService sesionAlumno;

    private AvanceAcademicoService service;
    private Curso curso;
    private Modulo modulo;
    private Matricula matricula;
    private Leccion l1;
    private Leccion l2;
    private Leccion l3;
    private Leccion l4;
    private Leccion l5;

    @BeforeEach
    void setUp() {
        service = new AvanceAcademicoService(rutaAcademica, sesionAlumno);
        curso = AcademiaFixtures.curso(200L, "EN_CURSO", "VIRTUAL");
        modulo = AcademiaFixtures.modulo(1L, curso, 1);
        matricula = AcademiaFixtures.matricula(5L, AcademiaFixtures.alumno(10L), curso, "ACTIVA");
        l1 = AcademiaFixtures.leccion(11L, modulo, 1, "VIDEO", true);
        l2 = AcademiaFixtures.leccion(12L, modulo, 2, "VIDEO", true);
        l3 = AcademiaFixtures.leccion(13L, modulo, 3, "VIDEO", true);
        l4 = AcademiaFixtures.leccion(14L, modulo, 4, "VIDEO", true);
        l5 = AcademiaFixtures.leccion(15L, modulo, 5, "VIDEO", true);
    }

    private ReglaCurso regla(boolean progreso, boolean examenes, boolean asistencia) {
        ReglaCurso regla = new ReglaCurso();
        regla.setRequiereProgreso(progreso);
        regla.setRequiereExamenes(examenes);
        regla.setRequiereAsistencia(asistencia);
        return regla;
    }

    private Map<Long, Boolean> completadas(int hechas) {
        Map<Long, Boolean> mapa = new HashMap<>();
        List<Leccion> todas = List.of(l1, l2, l3, l4, l5);
        for (int i = 0; i < todas.size(); i++) {
            mapa.put(todas.get(i).getId(), i < hechas);
        }
        return mapa;
    }

    private RutaAcademica ruta(ReglaCurso regla, int hechas, List<Examen> calificados, Set<Long> aprobados,
            Map<Long, BigDecimal> notas, Map<Long, EstadoExamen> estados) {
        return new RutaAcademica(regla, List.of(modulo), Map.of(modulo.getId(), List.of(l1, l2, l3, l4, l5)),
                List.of(l1, l2, l3, l4, l5), completadas(hechas), Map.of(), new HashMap<>(), List.of(), calificados,
                new HashSet<>(aprobados), notas, new LinkedHashMap<>(estados));
    }

    private Examen examenCalificado(Long id) {
        return AcademiaFixtures.examen(id, curso, null, "CALIFICADO", "FINAL");
    }

    private CondicionAvanceResponse condicion(AvanceAcademico avance, String codigo) {
        return avance.condiciones().stream().filter(c -> codigo.equals(c.codigo())).findFirst().orElseThrow();
    }

    @Test
    void conProgresoExamenYNotaSuficientesElAlumnoCumpleTodo() {
        Examen examen = examenCalificado(100L);
        RutaAcademica ruta = ruta(regla(true, true, false), 5, List.of(examen), Set.of(100L),
                Map.of(100L, new BigDecimal("16.00")), Map.of());

        AvanceAcademico avance = service.calcular(matricula, ruta);

        assertEquals(100, avance.porcentaje());
        assertTrue(avance.cumpleTodo());
        assertEquals("Cumples todos los requisitos activos para tu certificado.", avance.frase());
        assertEquals(List.of("PROGRESO", "EXAMENES", "NOTA"),
                avance.condiciones().stream().map(CondicionAvanceResponse::codigo).toList());
        assertTrue(avance.condiciones().stream().allMatch(CondicionAvanceResponse::cumplida));
        assertEquals(0, new BigDecimal("16.00").compareTo(avance.notaFinal()));
        assertEquals(1, avance.examenesAprobados());
        assertEquals(1, avance.examenesRequeridos());
    }

    @Test
    void conProgresoBajoElMinimoLaFraseDiceCuantoFalta() {
        RutaAcademica ruta = ruta(regla(true, false, false), 2, List.of(), Set.of(), Map.of(), Map.of());

        AvanceAcademico avance = service.calcular(matricula, ruta);

        assertEquals(40, avance.porcentaje());
        assertFalse(avance.cumpleTodo());
        assertFalse(condicion(avance, "PROGRESO").cumplida());
        assertEquals("40 %", condicion(avance, "PROGRESO").valorActual());
        assertEquals("mínimo 80 %", condicion(avance, "PROGRESO").meta());
        assertEquals("Te falta: completar al menos 80 % de las lecciones obligatorias (llevas 40 %).",
                avance.frase());
    }

    @Test
    void unaNotaPromedioPorDebajoDelMinimoNoCumpleAunqueApruebeElExamen() {
        Examen examen = examenCalificado(100L);
        RutaAcademica ruta = ruta(regla(false, true, false), 5, List.of(examen), Set.of(100L),
                Map.of(100L, new BigDecimal("10.00")), Map.of());

        AvanceAcademico avance = service.calcular(matricula, ruta);

        assertTrue(condicion(avance, "EXAMENES").cumplida());
        assertFalse(condicion(avance, "NOTA").cumplida());
        assertFalse(avance.cumpleTodo());
        assertEquals("Te falta: subir tu nota promedio a 12 (llevas 10).", avance.frase());
    }

    @Test
    void laNotaFinalEsElPromedioDeLasMejoresNotasDeLosExamenesCalificados() {
        Examen primero = examenCalificado(100L);
        Examen segundo = examenCalificado(101L);
        RutaAcademica ruta = ruta(regla(false, true, false), 5, List.of(primero, segundo), Set.of(100L, 101L),
                Map.of(100L, new BigDecimal("14.00"), 101L, new BigDecimal("17.00")), Map.of());

        AvanceAcademico avance = service.calcular(matricula, ruta);

        assertEquals(0, new BigDecimal("15.50").compareTo(avance.notaFinal()));
        assertEquals(2, avance.examenesAprobados());
        assertTrue(avance.cumpleTodo());
    }

    @Test
    void unExamenSinAprobarImpideCumplirYLaFraseLoIndica() {
        Examen examen = examenCalificado(100L);
        RutaAcademica ruta = ruta(regla(false, true, false), 5, List.of(examen), Set.of(), Map.of(), Map.of());

        AvanceAcademico avance = service.calcular(matricula, ruta);

        assertFalse(condicion(avance, "EXAMENES").cumplida());
        assertEquals("0 de 1 aprobados", condicion(avance, "EXAMENES").valorActual());
        assertNull(avance.notaFinal());
        assertEquals("Sin notas aún", condicion(avance, "NOTA").valorActual());
        assertEquals("Te falta: aprobar 1 examen más.", avance.frase());
    }

    @Test
    void unExamenConRespuestaAbiertaSinCalificarBloqueaLaCertificacion() {
        Examen examen = examenCalificado(100L);
        IntentoExamen enviado = new IntentoExamen();
        enviado.setMatricula(matricula);
        enviado.setExamen(examen);
        enviado.setNumeroIntento(1);
        enviado.setEstado(IntentoExamen.PENDIENTE_REVISION);
        enviado.setEnviadoEn(Instant.parse("2026-09-14T00:00:00Z"));
        EstadoExamen enRevision = new EstadoExamen(examen, List.of(enviado), 1, 2, null, enviado, false, null, false, null);
        RutaAcademica ruta = ruta(regla(false, true, false), 5, List.of(examen), Set.of(), Map.of(),
                Map.of(100L, enRevision));

        AvanceAcademico avance = service.calcular(matricula, ruta);

        assertTrue(avance.bloqueadoPorRevision());
        assertFalse(avance.cumpleTodo());
        assertEquals(1, avance.enRevision().size());
        assertTrue(avance.frase().contains("esperar la calificación de «Examen 100»"));
        assertTrue(condicion(avance, "EXAMENES").valorActual().contains("1 en revisión"));
    }

    @Test
    void enUnCursoEnVivoLaAsistenciaMinimaEsUnaCondicion() {
        Curso enVivo = AcademiaFixtures.curso(300L, "EN_CURSO", "EN_VIVO");
        Matricula matriculaEnVivo = AcademiaFixtures.matricula(6L, AcademiaFixtures.alumno(11L), enVivo, "ACTIVA");
        RutaAcademica ruta = ruta(regla(false, false, true), 5, List.of(), Set.of(), Map.of(), Map.of());
        when(sesionAlumno.porcentajeAsistencia(6L)).thenReturn(60);

        AvanceAcademico avance = service.calcular(matriculaEnVivo, ruta);

        assertTrue(avance.asistenciaExigida());
        assertEquals(60, avance.asistencia());
        assertFalse(condicion(avance, "ASISTENCIA").cumplida());
        assertEquals("Te falta: llegar a 80 % de asistencia (llevas 60 %).", avance.frase());
    }

    @Test
    void sinSesionesElegiblesLaAsistenciaNoAplicaPeroNuncaCuentaComoCumplida() {
        Curso enVivo = AcademiaFixtures.curso(300L, "EN_CURSO", "EN_VIVO");
        Matricula matriculaEnVivo = AcademiaFixtures.matricula(6L, AcademiaFixtures.alumno(11L), enVivo, "ACTIVA");
        RutaAcademica ruta = ruta(regla(false, false, true), 5, List.of(), Set.of(), Map.of(), Map.of());
        when(sesionAlumno.porcentajeAsistencia(6L)).thenReturn(null);

        AvanceAcademico avance = service.calcular(matriculaEnVivo, ruta);

        assertEquals("No aplica", condicion(avance, "ASISTENCIA").valorActual());
        assertFalse(condicion(avance, "ASISTENCIA").cumplida());
        assertFalse(avance.cumpleTodo());
    }

    @Test
    void enUnCursoVirtualLaAsistenciaNuncaSeCalculaAunqueLaReglaLaPida() {
        RutaAcademica ruta = ruta(regla(true, false, true), 5, List.of(), Set.of(), Map.of(), Map.of());

        AvanceAcademico avance = service.calcular(matricula, ruta);

        assertFalse(avance.asistenciaExigida());
        assertNull(avance.asistencia());
        assertEquals(List.of("PROGRESO"), avance.condiciones().stream().map(CondicionAvanceResponse::codigo).toList());
        verifyNoInteractions(sesionAlumno);
    }

    @Test
    void lasSesionesCanceladasYLasLeccionesOpcionalesNoCuentanParaElPorcentaje() {
        l4.setEstado("CANCELADA");
        l5.setEsObligatoria(false);
        RutaAcademica ruta = ruta(regla(true, false, false), 3, List.of(), Set.of(), Map.of(), Map.of());

        // Obligatorias vigentes: l1, l2, l3 (las tres completadas).
        assertEquals(100, AvanceAcademicoService.porcentaje(ruta));
    }
}
