package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import pe.edu.utp.escuela.app.dto.MaterialAlumnoResponse;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.ProgresoMaterialRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.service.MaterialAlumnoService.ContenidoMaterial;

/** EP04 · HU-023 — Materiales protegidos: sin matrícula con acceso vigente no se lista, ve ni descarga nada. */
@ExtendWith(MockitoExtension.class)
class MaterialAlumnoServiceTests {

    @Mock private LeccionRepository lecciones;
    @Mock private MaterialLeccionRepository materiales;
    @Mock private MatriculaRepository matriculas;
    @Mock private ProgresoMaterialRepository progresoMateriales;
    @Mock private ArchivoAlmacenamientoService almacenamiento;
    @Mock private CurrentUserService actual;

    private MaterialAlumnoService service;
    private final Clock clock = Clock.fixed(AcademiaFixtures.AHORA, ZoneId.of("America/Lima"));

    private Usuario usuario;
    private Curso curso;
    private Leccion leccion;

    @BeforeEach
    void setUp() {
        service = new MaterialAlumnoService(lecciones, materiales, matriculas, progresoMateriales, almacenamiento,
                actual, clock);
        usuario = AcademiaFixtures.alumno(10L);
        curso = AcademiaFixtures.curso(200L, "EN_CURSO", "VIRTUAL");
        Modulo modulo = AcademiaFixtures.modulo(1L, curso, 1);
        leccion = AcademiaFixtures.leccion(11L, modulo, 1, "VIDEO", true);
    }

    private void comoAlumno() {
        when(actual.get()).thenReturn(new CurrentUser(10L, "ana@example.com", Set.of("ALUMNO")));
    }

    private void conMatricula(String estado) {
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L))
                .thenReturn(Optional.of(AcademiaFixtures.matricula(5L, usuario, curso, estado)));
    }

    @Test
    void sinMatriculaNoSeListanLosMateriales() {
        comoAlumno();
        when(lecciones.findById(11L)).thenReturn(Optional.of(leccion));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listar(11L));

        verifyNoInteractions(materiales, almacenamiento);
    }

    @Test
    void sinMatriculaNoSeTransmiteElContenidoAunqueSeConozcaLaUrl() {
        comoAlumno();
        MaterialLeccion material = AcademiaFixtures.material(
                31L, leccion, AcademiaFixtures.recurso(41L, "VIDEO", "SUBIDO"), true);
        when(materiales.findById(31L)).thenReturn(Optional.of(material));
        when(matriculas.findByUsuario_IdAndCurso_Id(10L, 200L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.contenido(31L));

        verifyNoInteractions(almacenamiento);
    }

    @Test
    void matriculaInactivaBloqueaElMaterial() {
        comoAlumno();
        MaterialLeccion material = AcademiaFixtures.material(
                31L, leccion, AcademiaFixtures.recurso(41L, "VIDEO", "SUBIDO"), true);
        when(materiales.findById(31L)).thenReturn(Optional.of(material));
        conMatricula("PENDIENTE_PAGO");

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.descargar(31L));

        assertEquals("No tienes acceso a este contenido.", error.getMessage());
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void soloElAlumnoConsultaMateriales() {
        when(actual.get()).thenReturn(new CurrentUser(2L, "docente@example.com", Set.of("DOCENTE")));

        assertThrows(ForbiddenException.class, () -> service.listar(11L));

        verifyNoInteractions(lecciones, matriculas);
    }

    @Test
    void conMatriculaActivaSoloSeListanMaterialesActivosSinExponerLaRutaFisica() {
        comoAlumno();
        when(lecciones.findById(11L)).thenReturn(Optional.of(leccion));
        conMatricula("ACTIVA");
        MaterialLeccion subido = AcademiaFixtures.material(
                31L, leccion, AcademiaFixtures.recurso(41L, "VIDEO", "SUBIDO"), true);
        MaterialLeccion enlace = AcademiaFixtures.material(
                32L, leccion, AcademiaFixtures.recurso(42L, "VIDEO", "YOUTUBE"), false);
        MaterialLeccion retirado = AcademiaFixtures.material(
                33L, leccion, AcademiaFixtures.recurso(43L, "PDF", "SUBIDO"), true);
        retirado.setActivo(false);
        when(materiales.findByLeccion_IdOrderByOrdenAsc(11L)).thenReturn(List.of(subido, enlace, retirado));

        List<MaterialAlumnoResponse> respuesta = service.listar(11L);

        assertEquals(List.of(31L, 32L), respuesta.stream().map(MaterialAlumnoResponse::id).toList());
        assertEquals("/api/app/materiales/31/contenido", respuesta.get(0).verUrl());
        assertEquals("/api/app/materiales/31/descargar", respuesta.get(0).descargarUrl());
        assertEquals("https://youtu.be/clase-42", respuesta.get(1).verUrl());
        assertNull(respuesta.get(1).descargarUrl());
    }

    @Test
    void descargarExigeQueElMaterialPermitaDescarga() {
        comoAlumno();
        MaterialLeccion sinDescarga = AcademiaFixtures.material(
                31L, leccion, AcademiaFixtures.recurso(41L, "PDF", "SUBIDO"), false);
        when(materiales.findById(31L)).thenReturn(Optional.of(sinDescarga));
        conMatricula("ACTIVA");

        BusinessValidationException error =
                assertThrows(BusinessValidationException.class, () -> service.descargar(31L));

        assertEquals("Este material no permite descarga.", error.getMessage());
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void conMatriculaActivaElContenidoSeTransmiteDesdeElAlmacenamientoPrivado() {
        comoAlumno();
        MaterialLeccion material = AcademiaFixtures.material(
                31L, leccion, AcademiaFixtures.recurso(41L, "VIDEO", "SUBIDO"), true);
        when(materiales.findById(31L)).thenReturn(Optional.of(material));
        conMatricula("ACTIVA");
        Resource archivo = mock(Resource.class);
        when(almacenamiento.cargar("videos/clase-41.mp4")).thenReturn(archivo);

        ContenidoMaterial contenido = service.contenido(31L);

        assertSame(archivo, contenido.recurso());
        assertEquals("clase-41.mp4", contenido.nombreArchivo());
        assertEquals("video/mp4", contenido.tipoMime());
        verify(almacenamiento).cargar("videos/clase-41.mp4");
    }

    @Test
    void unMaterialExternoNoTieneContenidoParaTransmitir() {
        comoAlumno();
        MaterialLeccion enlace = AcademiaFixtures.material(
                32L, leccion, AcademiaFixtures.recurso(42L, "VIDEO", "YOUTUBE"), false);
        when(materiales.findById(32L)).thenReturn(Optional.of(enlace));
        conMatricula("ACTIVA");

        assertThrows(BusinessValidationException.class, () -> service.contenido(32L));

        verifyNoInteractions(almacenamiento);
    }
}
