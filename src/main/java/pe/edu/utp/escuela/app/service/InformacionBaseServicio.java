package pe.edu.utp.escuela.app.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ActualizarConfiguracionPeticion;
import pe.edu.utp.escuela.app.dto.CategoriaPeticion;
import pe.edu.utp.escuela.app.dto.CategoriaRespuesta;
import pe.edu.utp.escuela.app.dto.ConfiguracionPeticion;
import pe.edu.utp.escuela.app.dto.ConfiguracionRespuesta;
import pe.edu.utp.escuela.app.dto.DocentePeticion;
import pe.edu.utp.escuela.app.dto.DocenteRespuesta;
import pe.edu.utp.escuela.app.dto.EntidadPeticion;
import pe.edu.utp.escuela.app.dto.EntidadRespuesta;
import pe.edu.utp.escuela.app.dto.FirmantePeticion;
import pe.edu.utp.escuela.app.dto.FirmanteRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReglaArchivoPeticion;
import pe.edu.utp.escuela.app.dto.ReglaArchivoRespuesta;
import pe.edu.utp.escuela.app.dto.TipoCursoPeticion;
import pe.edu.utp.escuela.app.dto.TipoCursoRespuesta;
import pe.edu.utp.escuela.app.dto.TipoMaterialPeticion;
import pe.edu.utp.escuela.app.dto.TipoMaterialRespuesta;
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
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-009 — Administrar información base: datos maestros reutilizables por HU-010 en adelante.
 * Desactivar nunca borra: las referencias históricas (cursos, certificados ya emitidos) conservan
 * su copia aunque el elemento deje de ofrecerse para nuevas selecciones. */
@Service
@RequiredArgsConstructor
public class InformacionBaseServicio {

    private final TipoCursoRepositorio tiposCurso;
    private final CategoriaTematicaRepositorio categorias;
    private final PersonaRepositorio personas;
    private final EntidadCertificadoraRepositorio entidades;
    private final FirmanteRepositorio firmantes;
    private final TipoMaterialRepositorio tiposMaterial;
    private final ReglaArchivoRepositorio reglasArchivo;
    private final ConfiguracionInstitucionalRepositorio configuraciones;
    private final CurrentUserService currentUserService;
    private final TextNormalizer textos;

    // ---------------------------------------------------------------- Tipos de curso --
    @Transactional(readOnly = true)
    public PageResponse<TipoCursoRespuesta> listarTiposCurso(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<TipoCurso> pagina = incluirInactivos ? tiposCurso.findAllByOrderByOrdenAscNombreAsc(pageable)
                : tiposCurso.findByActivoTrueOrderByOrdenAscNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public TipoCursoRespuesta crearTipoCurso(TipoCursoPeticion p) {
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
    public TipoCursoRespuesta actualizarTipoCurso(Long id, TipoCursoPeticion p) {
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
    public TipoCursoRespuesta cambiarActivoTipoCurso(Long id, boolean activo) {
        exigirAdministrador();
        TipoCurso tipo = tiposCurso.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de curso ya no existe."));
        tipo.setActivo(activo);
        return mapear(tipo);
    }

    private TipoCursoRespuesta mapear(TipoCurso t) {
        return new TipoCursoRespuesta(t.getId(), t.getCodigo(), t.getNombre(), t.isActivo(), t.getOrden());
    }

    // ---------------------------------------------------------------- Categorías --
    @Transactional(readOnly = true)
    public PageResponse<CategoriaRespuesta> listarCategorias(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<CategoriaTematica> pagina = incluirInactivos ? categorias.findAllByOrderByOrdenAscNombreAsc(pageable)
                : categorias.findByActivoTrueOrderByOrdenAscNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public CategoriaRespuesta crearCategoria(CategoriaPeticion p) {
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
    public CategoriaRespuesta actualizarCategoria(Long id, CategoriaPeticion p) {
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
    public CategoriaRespuesta cambiarActivoCategoria(Long id, boolean activo) {
        exigirAdministrador();
        CategoriaTematica categoria = categorias.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La categoría ya no existe."));
        categoria.setActivo(activo);
        return mapear(categoria);
    }

    private CategoriaRespuesta mapear(CategoriaTematica c) {
        return new CategoriaRespuesta(c.getId(), c.getCodigo(), c.getNombre(), c.isActivo(), c.getOrden());
    }

    // ---------------------------------------------------------------- Docentes --
    /** Un docente es solo un perfil público (persona con cargo profesional): nunca crea cuenta,
     * acceso ni panel. */
    @Transactional(readOnly = true)
    public PageResponse<DocenteRespuesta> listarDocentes(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<Persona> pagina = incluirInactivos
                ? personas.findByCargoProfesionalIsNotNullOrderByNombresAsc(pageable)
                : personas.findByCargoProfesionalIsNotNullAndActivoTrueOrderByNombresAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public DocenteRespuesta crearDocente(DocentePeticion p) {
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
    public DocenteRespuesta actualizarDocente(Long personaId, DocentePeticion p) {
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
    public DocenteRespuesta cambiarActivoDocente(Long personaId, boolean activo) {
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

    private DocenteRespuesta mapear(Persona p) {
        return new DocenteRespuesta(p.getId(), p.getNombres(), p.getApellidoPaterno(),
                p.getApellidoMaterno(), p.nombreCompleto(), p.getFotoUrl(), p.getCargoProfesional(),
                p.getBiografiaProfesional(), p.isActivo());
    }

    // ---------------------------------------------------------------- Entidades certificadoras --
    @Transactional(readOnly = true)
    public PageResponse<EntidadRespuesta> listarEntidades(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<EntidadCertificadora> pagina = incluirInactivos ? entidades.findAllByOrderByNombreAsc(pageable)
                : entidades.findByActivoTrueOrderByNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public EntidadRespuesta crearEntidad(EntidadPeticion p) {
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
    public EntidadRespuesta actualizarEntidad(Long id, EntidadPeticion p) {
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
    public EntidadRespuesta cambiarActivoEntidad(Long id, boolean activo) {
        exigirAdministrador();
        EntidadCertificadora entidad = entidades.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La entidad ya no existe."));
        entidad.setActivo(activo);
        return mapear(entidad);
    }

    private EntidadRespuesta mapear(EntidadCertificadora e) {
        return new EntidadRespuesta(e.getId(), e.getNombre(), e.getLogoUrl(), e.isActivo());
    }

    // ---------------------------------------------------------------- Firmantes --
    @Transactional(readOnly = true)
    public PageResponse<FirmanteRespuesta> listarFirmantes(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<Firmante> pagina = incluirInactivos ? firmantes.findAllByOrderByIdAsc(pageable)
                : firmantes.findByActivoTrueOrderByIdAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public FirmanteRespuesta crearFirmante(FirmantePeticion p) {
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
    public FirmanteRespuesta actualizarFirmante(Long id, FirmantePeticion p) {
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
    public FirmanteRespuesta cambiarActivoFirmante(Long id, boolean activo) {
        exigirAdministrador();
        Firmante firmante = firmantes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El firmante ya no existe."));
        firmante.setActivo(activo);
        return mapear(firmante);
    }

    private FirmanteRespuesta mapear(Firmante f) {
        Persona p = f.getPersona();
        return new FirmanteRespuesta(f.getId(), p.getId(), p.getNombres(), p.getApellidoPaterno(),
                p.getApellidoMaterno(), p.nombreCompleto(), f.getCargoFirma(), f.getImagenFirmaUrl(), f.isActivo());
    }

    // ---------------------------------------------------------------- Tipos de material --
    @Transactional(readOnly = true)
    public PageResponse<TipoMaterialRespuesta> listarTiposMaterial(boolean incluirInactivos, Pageable pageable) {
        exigirAdministrador();
        Page<TipoMaterial> pagina = incluirInactivos ? tiposMaterial.findAllByOrderByNombreAsc(pageable)
                : tiposMaterial.findByActivoTrueOrderByNombreAsc(pageable);
        return PageResponse.from(pagina.getContent().stream().map(this::mapear).toList(), pagina);
    }

    @Transactional
    public TipoMaterialRespuesta crearTipoMaterial(TipoMaterialPeticion p) {
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
    public TipoMaterialRespuesta actualizarTipoMaterial(Long id, TipoMaterialPeticion p) {
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
    public TipoMaterialRespuesta cambiarActivoTipoMaterial(Long id, boolean activo) {
        exigirAdministrador();
        TipoMaterial tipo = tiposMaterial.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de material ya no existe."));
        tipo.setActivo(activo);
        return mapear(tipo);
    }

    private TipoMaterialRespuesta mapear(TipoMaterial t) {
        return new TipoMaterialRespuesta(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(), t.isActivo());
    }

    // ---------------------------------------------------------------- Reglas de archivo --
    @Transactional(readOnly = true)
    public List<ReglaArchivoRespuesta> listarReglasArchivo(boolean incluirInactivos) {
        exigirAdministrador();
        var fuente = incluirInactivos ? reglasArchivo.findAllByOrderByIdAsc()
                : reglasArchivo.findByActivoTrueOrderByIdAsc();
        return fuente.stream().map(this::mapear).toList();
    }

    @Transactional
    public ReglaArchivoRespuesta crearReglaArchivo(ReglaArchivoPeticion p) {
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
    public ReglaArchivoRespuesta actualizarReglaArchivo(Long id, ReglaArchivoPeticion p) {
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
    public ReglaArchivoRespuesta cambiarActivoReglaArchivo(Long id, boolean activo) {
        exigirAdministrador();
        ReglaArchivo regla = reglasArchivo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La regla ya no existe."));
        regla.setActivo(activo);
        return mapear(regla);
    }

    private ReglaArchivoRespuesta mapear(ReglaArchivo r) {
        return new ReglaArchivoRespuesta(r.getId(), r.getTipoMaterial().getId(),
                r.getTipoMaterial().getNombre(), r.getExtension(), r.getTamanoMaximoBytes(), r.isActivo());
    }

    // ---------------------------------------------------------------- Configuración institucional --
    @Transactional(readOnly = true)
    public List<ConfiguracionRespuesta> listarConfiguraciones() {
        exigirAdministrador();
        return configuraciones.findAllByOrderByCodigoAsc().stream().map(this::mapear).toList();
    }

    @Transactional
    public ConfiguracionRespuesta crearConfiguracion(ConfiguracionPeticion p) {
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
    public ConfiguracionRespuesta actualizarConfiguracion(Long id, ActualizarConfiguracionPeticion p) {
        exigirAdministrador();
        ConfiguracionInstitucional configuracion = configuraciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La configuración ya no existe."));
        configuracion.setValor(textos.requireText(p.valor(), "Valor"));
        configuracion.setDescripcion(textos.trimToNull(p.descripcion()));
        configuracion.setModificadoPorUsuarioId(currentUserService.get().userId());
        return mapear(configuracion);
    }

    private ConfiguracionRespuesta mapear(ConfiguracionInstitucional c) {
        return new ConfiguracionRespuesta(c.getId(), c.getCodigo(), c.getValor(), c.getDescripcion());
    }

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
