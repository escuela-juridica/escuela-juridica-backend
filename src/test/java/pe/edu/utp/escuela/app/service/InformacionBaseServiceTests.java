package pe.edu.utp.escuela.app.service;

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
import pe.edu.utp.escuela.app.dto.ActualizarConfiguracionRequest;
import pe.edu.utp.escuela.app.dto.CategoriaRequest;
import pe.edu.utp.escuela.app.dto.ConfiguracionRequest;
import pe.edu.utp.escuela.app.dto.ConfiguracionResponse;
import pe.edu.utp.escuela.app.dto.DocenteRequest;
import pe.edu.utp.escuela.app.dto.DocenteResponse;
import pe.edu.utp.escuela.app.dto.EntidadRequest;
import pe.edu.utp.escuela.app.dto.EntidadResponse;
import pe.edu.utp.escuela.app.dto.FirmanteRequest;
import pe.edu.utp.escuela.app.dto.FirmanteResponse;
import pe.edu.utp.escuela.app.dto.ReglaArchivoRequest;
import pe.edu.utp.escuela.app.dto.ReglaArchivoResponse;
import pe.edu.utp.escuela.app.dto.TipoCursoRequest;
import pe.edu.utp.escuela.app.dto.TipoCursoResponse;
import pe.edu.utp.escuela.app.dto.TipoMaterialRequest;
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
import pe.edu.utp.escuela.app.repository.CategoriaTematicaRepository;
import pe.edu.utp.escuela.app.repository.ConfiguracionInstitucionalRepository;
import pe.edu.utp.escuela.app.repository.EntidadCertificadoraRepository;
import pe.edu.utp.escuela.app.repository.FirmanteRepository;
import pe.edu.utp.escuela.app.repository.PersonaRepository;
import pe.edu.utp.escuela.app.repository.ReglaArchivoRepository;
import pe.edu.utp.escuela.app.repository.TipoCursoRepository;
import pe.edu.utp.escuela.app.repository.TipoMaterialRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@ExtendWith(MockitoExtension.class)
class InformacionBaseServiceTests {

    @Mock private TipoCursoRepository tiposCurso;
    @Mock private CategoriaTematicaRepository categorias;
    @Mock private PersonaRepository personas;
    @Mock private EntidadCertificadoraRepository entidades;
    @Mock private FirmanteRepository firmantes;
    @Mock private TipoMaterialRepository tiposMaterial;
    @Mock private ReglaArchivoRepository reglasArchivo;
    @Mock private ConfiguracionInstitucionalRepository configuraciones;
    @Mock private ArchivoAlmacenamientoService almacenamiento;
    @Mock private CurrentUserService currentUserService;

    private InformacionBaseService service;
    private final AtomicLong secuencia = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        service = new InformacionBaseService(tiposCurso, categorias, personas, entidades, firmantes,
                tiposMaterial, reglasArchivo, configuraciones, almacenamiento, currentUserService, new TextNormalizer());
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
        assertThrows(ForbiddenException.class, () -> service.listarTiposCurso(false, PageRequest.of(0, 20)));
    }

    // ---- Tipos de curso ----
    @Test
    void crearTipoCursoConCodigoDuplicadoLanzaConflicto() {
        comoAdministrador();
        when(tiposCurso.existsByCodigo("DIPLOMADO")).thenReturn(true);
        assertThrows(DuplicateResourceException.class,
                () -> service.crearTipoCurso(new TipoCursoRequest("DIPLOMADO", "Diplomado", 10)));
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

        TipoCursoResponse respuesta = service.cambiarActivoTipoCurso(5L, false);

        assertFalse(respuesta.activo());
        assertEquals("SEMINARIO", respuesta.codigo());
        org.mockito.Mockito.verify(tiposCurso, org.mockito.Mockito.never()).delete(any());
        org.mockito.Mockito.verify(tiposCurso, org.mockito.Mockito.never()).deleteById(any());
    }

    @Test
    void cambiarActivoTipoCursoInexistenteLanzaNoEncontrado() {
        comoAdministrador();
        when(tiposCurso.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.cambiarActivoTipoCurso(999L, false));
    }

    // ---- Categorías ----
    @Test
    void crearCategoriaConCodigoDuplicadoLanzaConflicto() {
        comoAdministrador();
        when(categorias.existsByCodigo("DERECHO_REGISTRAL")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.crearCategoria(
                new CategoriaRequest("DERECHO_REGISTRAL", "Derecho Registral", 10)));
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

        DocenteResponse respuesta = service.crearDocente(new DocenteRequest(
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

        DocenteResponse respuesta = service.cambiarActivoDocente(7L, false);

        assertFalse(respuesta.activo());
        org.mockito.Mockito.verify(personas, org.mockito.Mockito.never()).delete(any());
    }

    // ---- Entidades certificadoras ----
    @Test
    void crearEntidadConNombreDuplicadoLanzaConflicto() {
        comoAdministrador();
        when(entidades.existsByNombreIgnoreCase("Colegio de Abogados de Lima")).thenReturn(true);
        assertThrows(DuplicateResourceException.class,
                () -> service.crearEntidad(new EntidadRequest("Colegio de Abogados de Lima", null)));
    }

    @Test
    void desactivarEntidadUtilizadaConservaSuCopiaHistorica() {
        comoAdministrador();
        EntidadCertificadora entidad = new EntidadCertificadora();
        entidad.setId(3L);
        entidad.setNombre("Colegio de Abogados de Lima Sur");
        entidad.setActivo(true);
        when(entidades.findById(3L)).thenReturn(Optional.of(entidad));

        EntidadResponse respuesta = service.cambiarActivoEntidad(3L, false);

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

        FirmanteResponse respuesta = service.crearFirmante(new FirmanteRequest(
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

        assertThrows(DuplicateResourceException.class, () -> service.crearTipoMaterial(
                new TipoMaterialRequest("PDF", "Documento PDF", null)));
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

        ReglaArchivoResponse respuesta = service.crearReglaArchivo(
                new ReglaArchivoRequest(1L, ".PDF", 10_000_000L));

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
                () -> service.crearReglaArchivo(new ReglaArchivoRequest(1L, "pdf", 1_000L)));
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

        ConfiguracionResponse creada = service.crearConfiguracion(
                new ConfiguracionRequest("LUGAR_EMISION_CERTIFICADO", "Lima, Perú", "Lugar impreso en el certificado"));
        assertEquals("Lima, Perú", creada.valor());

        ConfiguracionInstitucional entidad = new ConfiguracionInstitucional();
        entidad.setId(1L);
        entidad.setCodigo("LUGAR_EMISION_CERTIFICADO");
        entidad.setValor("Lima, Perú");
        when(configuraciones.findById(1L)).thenReturn(Optional.of(entidad));

        ConfiguracionResponse actualizada = service.actualizarConfiguracion(
                1L, new ActualizarConfiguracionRequest("Lima, Perú, ESEJUR", null));
        assertEquals("Lima, Perú, ESEJUR", actualizada.valor());
    }
}
