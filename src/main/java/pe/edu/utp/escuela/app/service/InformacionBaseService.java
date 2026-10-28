package pe.edu.utp.escuela.app.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ActualizarConfiguracionRequest;
import pe.edu.utp.escuela.app.dto.CategoriaRequest;
import pe.edu.utp.escuela.app.dto.CategoriaResponse;
import pe.edu.utp.escuela.app.dto.ConfiguracionRequest;
import pe.edu.utp.escuela.app.dto.ConfiguracionResponse;
import pe.edu.utp.escuela.app.dto.DocenteRequest;
import pe.edu.utp.escuela.app.dto.DocenteResponse;
import pe.edu.utp.escuela.app.dto.EntidadRequest;
import pe.edu.utp.escuela.app.dto.EntidadResponse;
import pe.edu.utp.escuela.app.dto.FirmanteRequest;
import pe.edu.utp.escuela.app.dto.FirmanteResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReglaArchivoRequest;
import pe.edu.utp.escuela.app.dto.ReglaArchivoResponse;
import pe.edu.utp.escuela.app.dto.TipoCursoRequest;
import pe.edu.utp.escuela.app.dto.TipoCursoResponse;
import pe.edu.utp.escuela.app.dto.TipoMaterialRequest;
import pe.edu.utp.escuela.app.dto.TipoMaterialResponse;
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
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-009 — Administrar información base: datos maestros reutilizables por HU-010 en adelante.
 * Desactivar nunca borra: las referencias históricas (cursos, certificados ya emitidos) conservan
 * su copia aunque el elemento deje de ofrecerse para nuevas selecciones. */
@Service
@RequiredArgsConstructor
public class InformacionBaseService {

    private final TipoCursoRepository tiposCurso;
    private final CategoriaTematicaRepository categorias;
    private final PersonaRepository personas;
    private final EntidadCertificadoraRepository entidades;
    private final FirmanteRepository firmantes;
    private final TipoMaterialRepository tiposMaterial;
    private final ReglaArchivoRepository reglasArchivo;
    private final ConfiguracionInstitucionalRepository configuraciones;
    private final CurrentUserService currentUserService;
    private final TextNormalizer textos;

    // ---------------------------------------------------------------- Tipos de curso --
    @Transactional(readOnly = true)
    public PageResponse<TipoCursoResponse> listarTiposCurso(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<TipoCurso> pagina = incluirInactivos ? tiposCurso.findAllByOrderByOrdenAscNombreAsc(pageable)
                : tiposCurso.findByActivoTrueOrderByOrdenAscNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public TipoCursoResponse crearTipoCurso(TipoCursoRequest p) {
        exigirAdministrador();
        if (tiposCurso.existsByCodigo(p.codigo())) {
            throw new DuplicateResourceException("Ese código ya está en uso.");
        }
        TipoCurso tipo = new TipoCurso();
        tipo.setCodigo(textos.requireText(p.codigo(), "Código"));
        tipo.setNombre(textos.requireText(p.nombre(), "Nombre"));
        tipo.setOrden(p.orden());
        tipo.setActivo(true);
        tiposCurso.saveAndFlush(tipo);
        return mapear(tipo);
    }

    @Transactional
    public TipoCursoResponse actualizarTipoCurso(Long id, TipoCursoRequest p) {
        exigirAdministrador();
        TipoCurso tipo = tiposCurso.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de curso ya no existe."));
        if (tiposCurso.existsByCodigoAndIdNot(p.codigo(), id)) {
            throw new DuplicateResourceException("Ese código ya está en uso.");
        }
        tipo.setCodigo(textos.requireText(p.codigo(), "Código"));
        tipo.setNombre(textos.requireText(p.nombre(), "Nombre"));
        tipo.setOrden(p.orden());
        return mapear(tipo);
    }

    @Transactional
    public TipoCursoResponse cambiarActivoTipoCurso(Long id, boolean activo) {
        exigirAdministrador();
        TipoCurso tipo = tiposCurso.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de curso ya no existe."));
        tipo.setActivo(activo);
        return mapear(tipo);
    }

    private TipoCursoResponse mapear(TipoCurso t) {
        return new TipoCursoResponse(t.getId(), t.getCodigo(), t.getNombre(), t.isActivo(), t.getOrden());
    }

    // ---------------------------------------------------------------- Categorías --
    @Transactional(readOnly = true)
    public PageResponse<CategoriaResponse> listarCategorias(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<CategoriaTematica> pagina = incluirInactivos ? categorias.findAllByOrderByOrdenAscNombreAsc(pageable)
                : categorias.findByActivoTrueOrderByOrdenAscNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public CategoriaResponse crearCategoria(CategoriaRequest p) {
        exigirAdministrador();
        if (categorias.existsByCodigo(p.codigo())) {
            throw new DuplicateResourceException("Ese código ya está en uso.");
        }
        CategoriaTematica categoria = new CategoriaTematica();
        categoria.setCodigo(textos.requireText(p.codigo(), "Código"));
        categoria.setNombre(textos.requireText(p.nombre(), "Nombre"));
        categoria.setOrden(p.orden());
        categoria.setActivo(true);
        categorias.saveAndFlush(categoria);
        return mapear(categoria);
    }

    @Transactional
    public CategoriaResponse actualizarCategoria(Long id, CategoriaRequest p) {
        exigirAdministrador();
        CategoriaTematica categoria = categorias.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La categoría ya no existe."));
        if (categorias.existsByCodigoAndIdNot(p.codigo(), id)) {
            throw new DuplicateResourceException("Ese código ya está en uso.");
        }
        categoria.setCodigo(textos.requireText(p.codigo(), "Código"));
        categoria.setNombre(textos.requireText(p.nombre(), "Nombre"));
        categoria.setOrden(p.orden());
        return mapear(categoria);
    }

    @Transactional
    public CategoriaResponse cambiarActivoCategoria(Long id, boolean activo) {
        exigirAdministrador();
        CategoriaTematica categoria = categorias.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La categoría ya no existe."));
        categoria.setActivo(activo);
        return mapear(categoria);
    }

    private CategoriaResponse mapear(CategoriaTematica c) {
        return new CategoriaResponse(c.getId(), c.getCodigo(), c.getNombre(), c.isActivo(), c.getOrden());
    }

    // ---------------------------------------------------------------- Docentes --
    /** Un docente es solo un perfil público (persona con cargo profesional): nunca crea cuenta,
     * acceso ni panel. */
    @Transactional(readOnly = true)
    public PageResponse<DocenteResponse> listarDocentes(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<Persona> pagina = incluirInactivos
                ? personas.findByCargoProfesionalIsNotNullOrderByNombresAsc(pageable)
                : personas.findByCargoProfesionalIsNotNullAndActivoTrueOrderByNombresAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public DocenteResponse crearDocente(DocenteRequest p) {
        exigirAdministrador();
        Persona persona = new Persona();
        persona.setNombres(textos.requireText(p.nombres(), "Nombres"));
        persona.setApellidoPaterno(textos.requireText(p.apellidoPaterno(), "Apellido paterno"));
        persona.setApellidoMaterno(textos.trimToNull(p.apellidoMaterno()));
        persona.setFotoUrl(textos.trimToNull(p.fotoUrl()));
        persona.setCargoProfesional(textos.requireText(p.cargoProfesional(), "Cargo profesional"));
        persona.setBiografiaProfesional(textos.trimToNull(p.biografiaProfesional()));
        persona.setActivo(true);
        personas.saveAndFlush(persona);
        return mapear(persona);
    }

    @Transactional
    public DocenteResponse actualizarDocente(Long personaId, DocenteRequest p) {
        exigirAdministrador();
        Persona persona = docentePorId(personaId);
        persona.setNombres(textos.requireText(p.nombres(), "Nombres"));
        persona.setApellidoPaterno(textos.requireText(p.apellidoPaterno(), "Apellido paterno"));
        persona.setApellidoMaterno(textos.trimToNull(p.apellidoMaterno()));
        persona.setFotoUrl(textos.trimToNull(p.fotoUrl()));
        persona.setCargoProfesional(textos.requireText(p.cargoProfesional(), "Cargo profesional"));
        persona.setBiografiaProfesional(textos.trimToNull(p.biografiaProfesional()));
        return mapear(persona);
    }

    @Transactional
    public DocenteResponse cambiarActivoDocente(Long personaId, boolean activo) {
        exigirAdministrador();
        Persona persona = docentePorId(personaId);
        persona.setActivo(activo);
        return mapear(persona);
    }

    private Persona docentePorId(Long personaId) {
        Persona persona = personas.findById(personaId)
                .orElseThrow(() -> new ResourceNotFoundException("El docente ya no existe."));
        if (persona.getCargoProfesional() == null) {
            throw new ResourceNotFoundException("El docente ya no existe.");
        }
        return persona;
    }

    private DocenteResponse mapear(Persona p) {
        return new DocenteResponse(p.getId(), p.getNombres(), p.getApellidoPaterno(),
                p.getApellidoMaterno(), p.nombreCompleto(), p.getFotoUrl(), p.getCargoProfesional(),
                p.getBiografiaProfesional(), p.isActivo());
    }

    // ---------------------------------------------------------------- Entidades certificadoras --
    @Transactional(readOnly = true)
    public PageResponse<EntidadResponse> listarEntidades(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<EntidadCertificadora> pagina = incluirInactivos ? entidades.findAllByOrderByNombreAsc(pageable)
                : entidades.findByActivoTrueOrderByNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public EntidadResponse crearEntidad(EntidadRequest p) {
        exigirAdministrador();
        if (entidades.existsByNombreIgnoreCase(p.nombre())) {
            throw new DuplicateResourceException("Ya existe una entidad con ese nombre.");
        }
        EntidadCertificadora entidad = new EntidadCertificadora();
        entidad.setNombre(textos.requireText(p.nombre(), "Nombre"));
        entidad.setLogoUrl(textos.trimToNull(p.logoUrl()));
        entidad.setActivo(true);
        entidades.saveAndFlush(entidad);
        return mapear(entidad);
    }

    @Transactional
    public EntidadResponse actualizarEntidad(Long id, EntidadRequest p) {
        exigirAdministrador();
        EntidadCertificadora entidad = entidades.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La entidad ya no existe."));
        if (entidades.existsByNombreIgnoreCaseAndIdNot(p.nombre(), id)) {
            throw new DuplicateResourceException("Ya existe una entidad con ese nombre.");
        }
        entidad.setNombre(textos.requireText(p.nombre(), "Nombre"));
        entidad.setLogoUrl(textos.trimToNull(p.logoUrl()));
        return mapear(entidad);
    }

    @Transactional
    public EntidadResponse cambiarActivoEntidad(Long id, boolean activo) {
        exigirAdministrador();
        EntidadCertificadora entidad = entidades.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La entidad ya no existe."));
        entidad.setActivo(activo);
        return mapear(entidad);
    }

    private EntidadResponse mapear(EntidadCertificadora e) {
        return new EntidadResponse(e.getId(), e.getNombre(), e.getLogoUrl(), e.isActivo());
    }

    // ---------------------------------------------------------------- Firmantes --
    @Transactional(readOnly = true)
    public PageResponse<FirmanteResponse> listarFirmantes(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<Firmante> pagina = incluirInactivos ? firmantes.findAllByOrderByIdAsc(pageable)
                : firmantes.findByActivoTrueOrderByIdAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public FirmanteResponse crearFirmante(FirmanteRequest p) {
        exigirAdministrador();
        Persona persona = new Persona();
        persona.setNombres(textos.requireText(p.nombres(), "Nombres"));
        persona.setApellidoPaterno(textos.requireText(p.apellidoPaterno(), "Apellido paterno"));
        persona.setApellidoMaterno(textos.trimToNull(p.apellidoMaterno()));
        persona.setActivo(true);
        personas.saveAndFlush(persona);

        Firmante firmante = new Firmante();
        firmante.setPersona(persona);
        firmante.setCargoFirma(textos.requireText(p.cargoFirma(), "Cargo"));
        firmante.setImagenFirmaUrl(textos.trimToNull(p.imagenFirmaUrl()));
        firmante.setActivo(true);
        firmantes.saveAndFlush(firmante);
        return mapear(firmante);
    }

    @Transactional
    public FirmanteResponse actualizarFirmante(Long id, FirmanteRequest p) {
        exigirAdministrador();
        Firmante firmante = firmantes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El firmante ya no existe."));
        Persona persona = firmante.getPersona();
        persona.setNombres(textos.requireText(p.nombres(), "Nombres"));
        persona.setApellidoPaterno(textos.requireText(p.apellidoPaterno(), "Apellido paterno"));
        persona.setApellidoMaterno(textos.trimToNull(p.apellidoMaterno()));
        firmante.setCargoFirma(textos.requireText(p.cargoFirma(), "Cargo"));
        firmante.setImagenFirmaUrl(textos.trimToNull(p.imagenFirmaUrl()));
        return mapear(firmante);
    }

    @Transactional
    public FirmanteResponse cambiarActivoFirmante(Long id, boolean activo) {
        exigirAdministrador();
        Firmante firmante = firmantes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El firmante ya no existe."));
        firmante.setActivo(activo);
        return mapear(firmante);
    }

    private FirmanteResponse mapear(Firmante f) {
        Persona p = f.getPersona();
        return new FirmanteResponse(f.getId(), p.getId(), p.getNombres(), p.getApellidoPaterno(),
                p.getApellidoMaterno(), p.nombreCompleto(), f.getCargoFirma(), f.getImagenFirmaUrl(), f.isActivo());
    }

    // ---------------------------------------------------------------- Tipos de material --
    @Transactional(readOnly = true)
    public PageResponse<TipoMaterialResponse> listarTiposMaterial(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<TipoMaterial> pagina = incluirInactivos ? tiposMaterial.findAllByOrderByNombreAsc(pageable)
                : tiposMaterial.findByActivoTrueOrderByNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public TipoMaterialResponse crearTipoMaterial(TipoMaterialRequest p) {
        exigirAdministrador();
        if (tiposMaterial.findByCodigo(p.codigo()).isPresent()) {
            throw new DuplicateResourceException("Ese código ya está en uso.");
        }
        TipoMaterial tipo = new TipoMaterial();
        tipo.setCodigo(textos.requireText(p.codigo(), "Código"));
        tipo.setNombre(textos.requireText(p.nombre(), "Nombre"));
        tipo.setDescripcion(textos.trimToNull(p.descripcion()));
        tipo.setActivo(true);
        tiposMaterial.saveAndFlush(tipo);
        return mapear(tipo);
    }

    @Transactional
    public TipoMaterialResponse actualizarTipoMaterial(Long id, TipoMaterialRequest p) {
        exigirAdministrador();
        TipoMaterial tipo = tiposMaterial.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de material ya no existe."));
        tiposMaterial.findByCodigo(p.codigo())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new DuplicateResourceException("Ese código ya está en uso.");
                });
        tipo.setCodigo(textos.requireText(p.codigo(), "Código"));
        tipo.setNombre(textos.requireText(p.nombre(), "Nombre"));
        tipo.setDescripcion(textos.trimToNull(p.descripcion()));
        return mapear(tipo);
    }

    @Transactional
    public TipoMaterialResponse cambiarActivoTipoMaterial(Long id, boolean activo) {
        exigirAdministrador();
        TipoMaterial tipo = tiposMaterial.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de material ya no existe."));
        tipo.setActivo(activo);
        return mapear(tipo);
    }

    private TipoMaterialResponse mapear(TipoMaterial t) {
        return new TipoMaterialResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(), t.isActivo());
    }

    // ---------------------------------------------------------------- Reglas de archivo --
    @Transactional(readOnly = true)
    public List<ReglaArchivoResponse> listarReglasArchivo(boolean incluirInactivos) {
        exigirAdministrador();
        var fuente = incluirInactivos ? reglasArchivo.findAllByOrderByIdAsc()
                : reglasArchivo.findByActivoTrueOrderByIdAsc();
        return fuente.stream().map(this::mapear).toList();
    }

    @Transactional
    public ReglaArchivoResponse crearReglaArchivo(ReglaArchivoRequest p) {
        exigirAdministrador();
        TipoMaterial tipo = tiposMaterial.findById(p.tipoMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de material ya no existe."));
        String extension = textos.requireText(p.extension(), "Extensión").toLowerCase().replaceFirst("^\\.", "");
        if (reglasArchivo.existsByTipoMaterial_IdAndExtensionIgnoreCase(tipo.getId(), extension)) {
            throw new DuplicateResourceException("Ya existe una regla para esa extensión en este tipo.");
        }
        ReglaArchivo regla = new ReglaArchivo();
        regla.setTipoMaterial(tipo);
        regla.setExtension(extension);
        regla.setTamanoMaximoBytes(p.tamanoMaximoBytes());
        regla.setActivo(true);
        reglasArchivo.saveAndFlush(regla);
        return mapear(regla);
    }

    @Transactional
    public ReglaArchivoResponse actualizarReglaArchivo(Long id, ReglaArchivoRequest p) {
        exigirAdministrador();
        ReglaArchivo regla = reglasArchivo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La regla ya no existe."));
        TipoMaterial tipo = tiposMaterial.findById(p.tipoMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de material ya no existe."));
        String extension = textos.requireText(p.extension(), "Extensión").toLowerCase().replaceFirst("^\\.", "");
        if (reglasArchivo.existsByTipoMaterial_IdAndExtensionIgnoreCaseAndIdNot(tipo.getId(), extension, id)) {
            throw new DuplicateResourceException("Ya existe una regla para esa extensión en este tipo.");
        }
        regla.setTipoMaterial(tipo);
        regla.setExtension(extension);
        regla.setTamanoMaximoBytes(p.tamanoMaximoBytes());
        return mapear(regla);
    }

    @Transactional
    public ReglaArchivoResponse cambiarActivoReglaArchivo(Long id, boolean activo) {
        exigirAdministrador();
        ReglaArchivo regla = reglasArchivo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La regla ya no existe."));
        regla.setActivo(activo);
        return mapear(regla);
    }

    private ReglaArchivoResponse mapear(ReglaArchivo r) {
        return new ReglaArchivoResponse(r.getId(), r.getTipoMaterial().getId(),
                r.getTipoMaterial().getNombre(), r.getExtension(), r.getTamanoMaximoBytes(), r.isActivo());
    }

    // ---------------------------------------------------------------- Configuración institucional --
    @Transactional(readOnly = true)
    public List<ConfiguracionResponse> listarConfiguraciones() {
        exigirAdministrador();
        return configuraciones.findAllByOrderByCodigoAsc().stream().map(this::mapear).toList();
    }

    @Transactional
    public ConfiguracionResponse crearConfiguracion(ConfiguracionRequest p) {
        exigirAdministrador();
        if (configuraciones.existsByCodigo(p.codigo())) {
            throw new DuplicateResourceException("Ese código ya está en uso.");
        }
        ConfiguracionInstitucional configuracion = new ConfiguracionInstitucional();
        configuracion.setCodigo(textos.requireText(p.codigo(), "Código"));
        configuracion.setValor(textos.requireText(p.valor(), "Valor"));
        configuracion.setDescripcion(textos.trimToNull(p.descripcion()));
        configuracion.setModificadoPorUsuarioId(currentUserService.get().userId());
        configuraciones.saveAndFlush(configuracion);
        return mapear(configuracion);
    }

    @Transactional
    public ConfiguracionResponse actualizarConfiguracion(Long id, ActualizarConfiguracionRequest p) {
        exigirAdministrador();
        ConfiguracionInstitucional configuracion = configuraciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La configuración ya no existe."));
        configuracion.setValor(textos.requireText(p.valor(), "Valor"));
        configuracion.setDescripcion(textos.trimToNull(p.descripcion()));
        configuracion.setModificadoPorUsuarioId(currentUserService.get().userId());
        return mapear(configuracion);
    }

    private ConfiguracionResponse mapear(ConfiguracionInstitucional c) {
        return new ConfiguracionResponse(c.getId(), c.getCodigo(), c.getValor(), c.getDescripcion());
    }

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
