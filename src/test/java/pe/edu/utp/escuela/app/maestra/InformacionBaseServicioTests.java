package pe.edu.utp.escuela.app.maestra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import pe.edu.utp.escuela.app.entity.CategoriaTematica;
import pe.edu.utp.escuela.app.entity.ConfiguracionInstitucional;
import pe.edu.utp.escuela.app.entity.EntidadCertificadora;
import pe.edu.utp.escuela.app.entity.Firmante;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.ReglaArchivo;
import pe.edu.utp.escuela.app.entity.TipoCurso;
import pe.edu.utp.escuela.app.entity.TipoMaterial;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CategoriaTematicaRepositorio;
import pe.edu.utp.escuela.app.repository.ConfiguracionInstitucionalRepositorio;
import pe.edu.utp.escuela.app.repository.EntidadCertificadoraRepositorio;
import pe.edu.utp.escuela.app.repository.FirmanteRepositorio;
import pe.edu.utp.escuela.app.repository.PersonaRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaArchivoRepositorio;
import pe.edu.utp.escuela.app.repository.TipoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.TipoMaterialRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@ExtendWith(MockitoExtension.class)
class InformacionBaseServicioTests {

    @Mock private TipoCursoRepositorio tiposCurso;
    @Mock private CategoriaTematicaRepositorio categorias;
    @Mock private PersonaRepositorio personas;
    @Mock private EntidadCertificadoraRepositorio entidades;
    @Mock private FirmanteRepositorio firmantes;
    @Mock private TipoMaterialRepositorio tiposMaterial;
    @Mock private ReglaArchivoRepositorio reglasArchivo;
    @Mock private ConfiguracionInstitucionalRepositorio configuraciones;
    @Mock private CurrentUserService currentUserService;

    private InformacionBaseServicio servicio;
    private final AtomicLong secuencia = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        servicio = new InformacionBaseServicio(tiposCurso, categorias, personas, entidades, firmantes,
                tiposMaterial, reglasArchivo, configuraciones, currentUserService, new TextNormalizer());
    }

    private void comoAdministrador() {
        when(currentUserService.get())
                .thenReturn(new CurrentUser(1L, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    private void comoAlumno() {
        when(currentUserService.get())
                .thenReturn(new CurrentUser(2L, "alumno@example.com", Set.of("ALUMNO")));
    }

    @Test
    void cualquierOperacionSinRolAdministradorLanzaProhibido() {
        comoAlumno();
        assertThrows(ForbiddenException.class, () -> servicio.listarTiposCurso(false, PageRequest.of(0, 20)));
    }

    // ---- Tipos de curso ----
    @Test
    void crearTipoCursoConCodigoDuplicadoLanzaConflicto() {
        comoAdministrador();
        when(tiposCurso.existsByCodigo("DIPLOMADO")).thenReturn(true);
        assertThrows(DuplicateResourceException.class,
                () -> servicio.crearTipoCurso(new TipoCursoPeticion("DIPLOMADO", "Diplomado", 10)));
    }

    @Test
    void desactivarTipoCursoNoLoEliminaSoloCambiaActivo() {
        comoAdministrador();
        TipoCurso tipo = new TipoCurso();
        tipo.setId(5L);
        tipo.setCodigo("SEMINARIO");
        tipo.setNombre("Seminario");
        tipo.setActivo(true);
        tipo.setOrden(50);
        when(tiposCurso.findById(5L)).thenReturn(Optional.of(tipo));

        TipoCursoRespuesta respuesta = servicio.cambiarActivoTipoCurso(5L, false);

        assertFalse(respuesta.activo());
        assertEquals("SEMINARIO", respuesta.codigo());
        org.mockito.Mockito.verify(tiposCurso, org.mockito.Mockito.never()).delete(any());
        org.mockito.Mockito.verify(tiposCurso, org.mockito.Mockito.never()).deleteById(any());
    }

    @Test
    void cambiarActivoTipoCursoInexistenteLanzaNoEncontrado() {
        comoAdministrador();
        when(tiposCurso.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> servicio.cambiarActivoTipoCurso(999L, false));
    }

    // ---- Categorías ----
    @Test
    void crearCategoriaConCodigoDuplicadoLanzaConflicto() {
        comoAdministrador();
        when(categorias.existsByCodigo("DERECHO_REGISTRAL")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> servicio.crearCategoria(
                new CategoriaPeticion("DERECHO_REGISTRAL", "Derecho Registral", 10)));
    }

    // ---- Docentes ----
    @Test
    void crearDocenteNoCreaUsuarioDeAcceso() {
        comoAdministrador();
        when(personas.saveAndFlush(any())).thenAnswer(inv -> {
            Persona p = inv.getArgument(0);
            p.setId(secuencia.incrementAndGet());
            return p;
        });

        DocenteRespuesta respuesta = servicio.crearDocente(new DocentePeticion(
                "Milagros Fernanda", "Salvatierra", "Guevara", null,
                "Docente especialista en Derecho Registral", "Biografía de ejemplo."));

        assertEquals("Milagros Fernanda Salvatierra Guevara", respuesta.nombreCompleto());
        assertTrue(respuesta.activo());
        assertEquals("Docente especialista en Derecho Registral", respuesta.cargoProfesional());
    }

    @Test
    void desactivarDocenteConservaElPerfil() {
        comoAdministrador();
        Persona persona = new Persona();
        persona.setId(7L);
        persona.setNombres("Ariana");
        persona.setApellidoPaterno("Lazaro");
        persona.setCargoProfesional("Docente especialista en Litigación");
        persona.setActivo(true);
        when(personas.findById(7L)).thenReturn(Optional.of(persona));

        DocenteRespuesta respuesta = servicio.cambiarActivoDocente(7L, false);

        assertFalse(respuesta.activo());
        org.mockito.Mockito.verify(personas, org.mockito.Mockito.never()).delete(any());
    }

    // ---- Entidades certificadoras ----
    @Test
    void crearEntidadConNombreDuplicadoLanzaConflicto() {
        comoAdministrador();
        when(entidades.existsByNombreIgnoreCase("Colegio de Abogados de Lima")).thenReturn(true);
        assertThrows(DuplicateResourceException.class,
                () -> servicio.crearEntidad(new EntidadPeticion("Colegio de Abogados de Lima", null)));
    }

    @Test
    void desactivarEntidadUtilizadaConservaSuCopiaHistorica() {
        comoAdministrador();
        EntidadCertificadora entidad = new EntidadCertificadora();
        entidad.setId(3L);
        entidad.setNombre("Colegio de Abogados de Lima Sur");
        entidad.setActivo(true);
        when(entidades.findById(3L)).thenReturn(Optional.of(entidad));

        EntidadRespuesta respuesta = servicio.cambiarActivoEntidad(3L, false);

        assertFalse(respuesta.activo());
        assertEquals("Colegio de Abogados de Lima Sur", respuesta.nombre());
        org.mockito.Mockito.verify(entidades, org.mockito.Mockito.never()).delete(any());
    }

    // ---- Firmantes ----
    @Test
    void crearFirmanteCreaPersonaYFirmanteVinculados() {
        comoAdministrador();
        when(personas.saveAndFlush(any())).thenAnswer(inv -> {
            Persona p = inv.getArgument(0);
            p.setId(secuencia.incrementAndGet());
            return p;
        });
        when(firmantes.saveAndFlush(any())).thenAnswer(inv -> {
            Firmante f = inv.getArgument(0);
            f.setId(secuencia.incrementAndGet());
            return f;
        });

        FirmanteRespuesta respuesta = servicio.crearFirmante(new FirmantePeticion(
                "Lilia Mercedes", "Guerra", "Macedo", "Directora Ejecutiva", null));

        assertEquals("Directora Ejecutiva", respuesta.cargoFirma());
        assertTrue(respuesta.activo());
    }

    // ---- Tipos de material ----
    @Test
    void crearTipoMaterialConCodigoDuplicadoLanzaConflicto() {
        comoAdministrador();
        TipoMaterial existente = new TipoMaterial();
        existente.setId(1L);
        existente.setCodigo("PDF");
        when(tiposMaterial.findByCodigo("PDF")).thenReturn(Optional.of(existente));

        assertThrows(DuplicateResourceException.class, () -> servicio.crearTipoMaterial(
                new TipoMaterialPeticion("PDF", "Documento PDF", null)));
    }

    // ---- Reglas de archivo ----
    @Test
    void crearReglaArchivoNormalizaExtensionYRechazaDuplicada() {
        comoAdministrador();
        TipoMaterial tipo = new TipoMaterial();
        tipo.setId(1L);
        tipo.setCodigo("PDF");
        tipo.setNombre("Documento PDF");
        when(tiposMaterial.findById(1L)).thenReturn(Optional.of(tipo));
        when(reglasArchivo.existsByTipoMaterial_IdAndExtensionIgnoreCase(1L, "pdf")).thenReturn(false);
        when(reglasArchivo.saveAndFlush(any())).thenAnswer(inv -> {
            ReglaArchivo r = inv.getArgument(0);
            r.setId(secuencia.incrementAndGet());
            return r;
        });

        ReglaArchivoRespuesta respuesta = servicio.crearReglaArchivo(
                new ReglaArchivoPeticion(1L, ".PDF", 10_000_000L));

        assertEquals("pdf", respuesta.extension());
    }

    @Test
    void crearReglaArchivoDuplicadaParaElMismoTipoLanzaConflicto() {
        comoAdministrador();
        TipoMaterial tipo = new TipoMaterial();
        tipo.setId(1L);
        tipo.setCodigo("PDF");
        when(tiposMaterial.findById(1L)).thenReturn(Optional.of(tipo));
        when(reglasArchivo.existsByTipoMaterial_IdAndExtensionIgnoreCase(1L, "pdf")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> servicio.crearReglaArchivo(new ReglaArchivoPeticion(1L, "pdf", 1_000L)));
    }

    // ---- Configuración institucional ----
    @Test
    void crearYActualizarConfiguracionInstitucional() {
        comoAdministrador();
        when(configuraciones.existsByCodigo("LUGAR_EMISION_CERTIFICADO")).thenReturn(false);
        when(configuraciones.saveAndFlush(any())).thenAnswer(inv -> {
            ConfiguracionInstitucional c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        ConfiguracionRespuesta creada = servicio.crearConfiguracion(
                new ConfiguracionPeticion("LUGAR_EMISION_CERTIFICADO", "Lima, Perú", "Lugar impreso en el certificado"));
        assertEquals("Lima, Perú", creada.valor());

        ConfiguracionInstitucional entidad = new ConfiguracionInstitucional();
        entidad.setId(1L);
        entidad.setCodigo("LUGAR_EMISION_CERTIFICADO");
        entidad.setValor("Lima, Perú");
        when(configuraciones.findById(1L)).thenReturn(Optional.of(entidad));

        ConfiguracionRespuesta actualizada = servicio.actualizarConfiguracion(
                1L, new ActualizarConfiguracionPeticion("Lima, Perú, ESEJUR", null));
        assertEquals("Lima, Perú, ESEJUR", actualizada.valor());
    }
}
