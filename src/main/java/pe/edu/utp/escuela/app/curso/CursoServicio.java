package pe.edu.utp.escuela.app.curso;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.entity.CategoriaTematica;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.CursoDocente;
import pe.edu.utp.escuela.app.entity.CursoFirmante;
import pe.edu.utp.escuela.app.entity.EntidadCertificadora;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Firmante;
import pe.edu.utp.escuela.app.entity.HistorialEstadoCurso;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.TipoCurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CategoriaTematicaRepositorio;
import pe.edu.utp.escuela.app.repository.CursoDocenteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoFirmanteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.EntidadCertificadoraRepositorio;
import pe.edu.utp.escuela.app.repository.EstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.FirmanteRepositorio;
import pe.edu.utp.escuela.app.repository.HistorialEstadoCursoRepositorio;
import pe.edu.utp.escuela.app.repository.PersonaRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.repository.TipoCursoRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-010 — Crear y configurar un curso: alta en BORRADOR y edición de su información comercial y
 * temporal, docentes y firmantes. El contenido (HU-011), sesiones (HU-012), exámenes (HU-013),
 * requisitos de certificación (HU-014) y publicación (HU-015) son historias aparte. */
@Service
@RequiredArgsConstructor
public class CursoServicio {

    private static final String ESTADO_BORRADOR = "BORRADOR";

    private final CursoRepositorio cursos;
    private final CursoDocenteRepositorio cursoDocentes;
    private final CursoFirmanteRepositorio cursoFirmantes;
    private final ReglaCursoRepositorio reglasCurso;
    private final HistorialEstadoCursoRepositorio historial;
    private final EstadoCursoRepositorio estadosCurso;
    private final TipoCursoRepositorio tiposCurso;
    private final CategoriaTematicaRepositorio categorias;
    private final EntidadCertificadoraRepositorio entidades;
    private final PersonaRepositorio personas;
    private final FirmanteRepositorio firmantes;
    private final CurrentUserService currentUserService;
    private final TextNormalizer textos;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CursoResumenRespuesta> listar(String texto, Pageable pageable) {
        exigirAdministrador();
        String termino = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        Page<Curso> pagina = cursos.buscarAdministrativos(termino, pageable);
        List<CursoResumenRespuesta> items = pagina.getContent().stream().map(this::resumenDe).toList();
        return PageResponse.from(items, pagina);
    }

    @Transactional
    public CursoEditorRespuesta crear(CrearCursoPeticion p) {
        exigirAdministrador();
        String titulo = textos.requireText(p.titulo(), "Título");
        EstadoCurso borrador = estadosCurso.findByCodigo(ESTADO_BORRADOR)
                .orElseThrow(() -> new IllegalStateException("Falta el estado BORRADOR"));

        Curso curso = new Curso();
        curso.setTitulo(titulo);
        curso.setUrlAmigable(generarUrlAmigableUnica(titulo, null));
        curso.setEstadoCurso(borrador);
        curso.setPrecioRegular(BigDecimal.ZERO);
        curso.setCreadoPorUsuarioId(currentUserService.get().userId());
        cursos.saveAndFlush(curso);

        ReglaCurso regla = new ReglaCurso();
        regla.setCurso(curso);
        reglasCurso.saveAndFlush(regla);

        registrarHistorial(curso, null, borrador, "Curso creado como borrador.");

        return detalleDe(curso);
    }

    @Transactional(readOnly = true)
    public CursoEditorRespuesta obtener(Long cursoId) {
        exigirAdministrador();
        return detalleDe(buscarOLanzar(cursoId));
    }

    @Transactional
    public CursoEditorRespuesta actualizarInformacion(Long cursoId, ActualizarInformacionCursoPeticion p) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);

        LocalDate fechaFin = p.modalidad() == ModalidadCurso.VIRTUAL ? null : p.fechaFin();
        LocalDate fechaCierre = p.modalidad() == ModalidadCurso.VIRTUAL ? null : p.fechaCierreMatricula();
        validarModalidadYFechas(p.modalidad(), p.fechaInicio(), fechaFin, fechaCierre);
        validarPrecios(p.tipoVenta(), p.precioRegular(), p.precioPromocional(), p.promocionInicioEn(), p.promocionFinEn());
        validarCapacidadYVigencia(p.cupoMaximo(), p.vigenciaAccesoDias());

        String urlSolicitada = textos.trimToNull(p.urlAmigable());
        String url = urlSolicitada != null ? normalizarSlug(urlSolicitada) : curso.getUrlAmigable();
        if (url.isEmpty()) {
            throw new BusinessValidationException("La URL amigable no puede quedar vacía.");
        }
        if (!url.equals(curso.getUrlAmigable()) && cursos.existsByUrlAmigableAndIdNot(url, cursoId)) {
            throw new DuplicateResourceException("Esa URL ya está en uso por otro curso.");
        }

        curso.setTitulo(textos.requireText(p.titulo(), "Título"));
        curso.setUrlAmigable(url);
        curso.setDescripcion(textos.trimToNull(p.descripcion()));
        curso.setImagenPortadaUrl(textos.trimToNull(p.imagenPortadaUrl()));
        curso.setTipoCurso(referenciaOLanzar(p.tipoCursoId(), tiposCurso::findById, "El tipo de curso ya no existe."));
        curso.setCategoriaTematica(referenciaOLanzar(p.categoriaTematicaId(), categorias::findById, "La categoría ya no existe."));
        curso.setEntidadCertificadora(referenciaOLanzar(p.entidadCertificadoraId(), entidades::findById, "La entidad certificadora ya no existe."));
        curso.setModalidad(p.modalidad().name());
        curso.setTipoVenta(p.tipoVenta().name());
        curso.setDestacado(p.destacado());
        curso.setPrecioRegular(p.precioRegular());
        curso.setPrecioPromocional(p.precioPromocional());
        curso.setPromocionInicioEn(p.promocionInicioEn());
        curso.setPromocionFinEn(p.promocionFinEn());
        curso.setFechaInicio(p.fechaInicio());
        curso.setFechaFin(fechaFin);
        curso.setFechaCierreMatricula(fechaCierre);
        curso.setCupoMaximo(p.cupoMaximo());
        curso.setHorasAcademicas(p.horasAcademicas());
        curso.setVigenciaAccesoDias(p.vigenciaAccesoDias());
        curso.setBeneficios(p.beneficios() == null ? new String[0] : p.beneficios().stream()
                .map(textos::trimToNull).filter(b -> b != null).toArray(String[]::new));

        return detalleDe(curso);
    }

    @Transactional
    public CursoEditorRespuesta actualizarDocentes(Long cursoId, AsignarDocentesPeticion p) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        List<Long> ids = p.personaIds();
        if (ids.stream().distinct().count() != ids.size()) {
            throw new BusinessValidationException("Un docente no puede asignarse dos veces al mismo curso.");
        }
        List<Persona> encontradas = personas.findAllById(ids);
        if (encontradas.size() != ids.size()) {
            throw new ResourceNotFoundException("Alguno de los docentes ya no existe.");
        }
        Map<Long, Persona> porId = encontradas.stream().collect(Collectors.toMap(Persona::getId, x -> x));
        for (Long id : ids) {
            Persona persona = porId.get(id);
            if (!persona.isActivo() || persona.getCargoProfesional() == null) {
                throw new BusinessValidationException("Solo se pueden asignar docentes activos.");
            }
        }

        cursoDocentes.deleteAllByCurso_Id(cursoId);
        cursoDocentes.flush();
        int orden = 1;
        for (Long id : ids) {
            CursoDocente asignacion = new CursoDocente();
            asignacion.setCurso(curso);
            asignacion.setPersona(porId.get(id));
            asignacion.setOrden(orden++);
            cursoDocentes.save(asignacion);
        }

        return detalleDe(curso);
    }

    @Transactional
    public CursoEditorRespuesta actualizarFirmantes(Long cursoId, AsignarFirmantesPeticion p) {
        exigirAdministrador();
        Curso curso = buscarOLanzar(cursoId);
        List<Long> ids = p.firmanteIds() == null ? List.of() : p.firmanteIds();
        if (ids.stream().distinct().count() != ids.size()) {
            throw new BusinessValidationException("Un firmante no puede asignarse dos veces al mismo curso.");
        }
        List<Firmante> encontrados = firmantes.findAllById(ids);
        if (encontrados.size() != ids.size()) {
            throw new ResourceNotFoundException("Alguno de los firmantes ya no existe.");
        }
        Map<Long, Firmante> porId = encontrados.stream().collect(Collectors.toMap(Firmante::getId, x -> x));
        for (Long id : ids) {
            if (!porId.get(id).isActivo()) {
                throw new BusinessValidationException("Solo se pueden asignar firmantes activos.");
            }
        }

        cursoFirmantes.deleteAllByCurso_Id(cursoId);
        cursoFirmantes.flush();
        int orden = 1;
        for (Long id : ids) {
            CursoFirmante asignacion = new CursoFirmante();
            asignacion.setCurso(curso);
            asignacion.setFirmante(porId.get(id));
            asignacion.setOrden(orden++);
            cursoFirmantes.save(asignacion);
        }

        return detalleDe(curso);
    }

    // ---------------------------------------------------------------- Validaciones --

    private void validarModalidadYFechas(ModalidadCurso modalidad, LocalDate inicio, LocalDate fin, LocalDate cierre) {
        if (modalidad == ModalidadCurso.VIRTUAL) {
            if (fin != null) {
                throw new BusinessValidationException("Un curso virtual no admite fecha de fin.");
            }
            return;
        }
        if (inicio == null || fin == null) {
            throw new BusinessValidationException("La modalidad EN_VIVO o HIBRIDO exige fecha de inicio y de fin.");
        }
        if (fin.isBefore(inicio)) {
            throw new BusinessValidationException("La fecha de fin no puede ser anterior a la de inicio.");
        }
        if (cierre != null && cierre.isAfter(fin)) {
            throw new BusinessValidationException("El cierre de matrícula no puede ser posterior a la fecha de fin.");
        }
    }

    private void validarPrecios(
            TipoVentaCurso tipoVenta, BigDecimal regular, BigDecimal promocional,
            Instant promoInicio, Instant promoFin) {
        if (regular == null || regular.signum() < 0) {
            throw new BusinessValidationException("El precio regular no puede ser negativo.");
        }
        if (tipoVenta == TipoVentaCurso.GRATUITO && regular.signum() != 0) {
            throw new BusinessValidationException("Un curso gratuito no puede tener precio regular.");
        }
        if (promocional != null) {
            if (promocional.signum() < 0) {
                throw new BusinessValidationException("El precio promocional no puede ser negativo.");
            }
            if (promocional.compareTo(regular) > 0) {
                throw new BusinessValidationException("El precio promocional no puede ser mayor que el precio regular.");
            }
        }
        boolean algunaFechaPromo = promoInicio != null || promoFin != null;
        if (algunaFechaPromo) {
            if (promoInicio == null || promoFin == null) {
                throw new BusinessValidationException("La vigencia de la promoción exige inicio y fin.");
            }
            if (promoInicio.isAfter(promoFin)) {
                throw new BusinessValidationException("El inicio de la promoción no puede ser posterior a su fin.");
            }
        }
    }

    private void validarCapacidadYVigencia(Integer cupoMaximo, Integer vigenciaDias) {
        if (cupoMaximo != null && cupoMaximo <= 0) {
            throw new BusinessValidationException("La capacidad debe ser mayor que cero.");
        }
        if (vigenciaDias != null && vigenciaDias <= 0) {
            throw new BusinessValidationException("La vigencia de acceso debe ser mayor que cero días.");
        }
    }

    // ---------------------------------------------------------------- Soporte --

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private Curso buscarOLanzar(Long cursoId) {
        return cursos.findWithDetalleById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso ya no existe."));
    }

    private <T> T referenciaOLanzar(
            Long id, java.util.function.Function<Long, java.util.Optional<T>> buscador, String mensajeError) {
        if (id == null) {
            return null;
        }
        return buscador.apply(id).orElseThrow(() -> new ResourceNotFoundException(mensajeError));
    }

    private void registrarHistorial(Curso curso, EstadoCurso anterior, EstadoCurso nuevo, String motivo) {
        HistorialEstadoCurso h = new HistorialEstadoCurso();
        h.setCurso(curso);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(currentUserService.get().userId());
        h.setRealizadoEn(clock.instant());
        historial.saveAndFlush(h);
    }

    private String normalizarSlug(String valor) {
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug;
    }

    private String generarUrlAmigableUnica(String titulo, Long idActual) {
        String base = normalizarSlug(titulo);
        if (base.isEmpty()) {
            base = "curso";
        }
        String candidato = base;
        int sufijo = 2;
        while (idActual == null ? cursos.existsByUrlAmigable(candidato) : cursos.existsByUrlAmigableAndIdNot(candidato, idActual)) {
            candidato = base + "-" + sufijo++;
        }
        return candidato;
    }

    private CursoResumenRespuesta resumenDe(Curso c) {
        return new CursoResumenRespuesta(
                c.getId(),
                c.getUrlAmigable(),
                c.getTitulo(),
                c.getEstadoCurso().getCodigo(),
                c.getEstadoCurso().getNombre(),
                c.getModalidad(),
                c.getTipoVenta(),
                c.getTipoCurso() == null ? null : c.getTipoCurso().getNombre(),
                c.getCategoriaTematica() == null ? null : c.getCategoriaTematica().getNombre(),
                c.getPrecioRegular(),
                c.getPublicadoEn() != null,
                c.getCreadoEn());
    }

    private CursoEditorRespuesta detalleDe(Curso c) {
        List<DocenteCursoRespuesta> docentes = cursoDocentes.findByCurso_IdOrderByOrdenAsc(c.getId()).stream()
                .map(cd -> new DocenteCursoRespuesta(
                        cd.getPersona().getId(),
                        cd.getPersona().nombreCompleto(),
                        cd.getPersona().getFotoUrl(),
                        cd.getPersona().getCargoProfesional(),
                        cd.getOrden(),
                        cd.getPersona().isActivo()))
                .toList();
        List<FirmanteCursoRespuesta> firmantesCurso = cursoFirmantes.findByCurso_IdOrderByOrdenAsc(c.getId()).stream()
                .map(cf -> new FirmanteCursoRespuesta(
                        cf.getFirmante().getId(),
                        cf.getFirmante().getPersona().nombreCompleto(),
                        cf.getFirmante().getCargoFirma(),
                        cf.getOrden(),
                        cf.getFirmante().isActivo()))
                .toList();

        TipoCurso tipoCurso = c.getTipoCurso();
        CategoriaTematica categoria = c.getCategoriaTematica();
        EntidadCertificadora entidad = c.getEntidadCertificadora();

        return new CursoEditorRespuesta(
                c.getId(),
                c.getUrlAmigable(),
                c.getTitulo(),
                c.getDescripcion(),
                c.getImagenPortadaUrl(),
                tipoCurso == null ? null : tipoCurso.getId(),
                tipoCurso == null ? null : tipoCurso.getNombre(),
                categoria == null ? null : categoria.getId(),
                categoria == null ? null : categoria.getNombre(),
                entidad == null ? null : entidad.getId(),
                entidad == null ? null : entidad.getNombre(),
                c.getModalidad() == null ? null : ModalidadCurso.valueOf(c.getModalidad()),
                c.getTipoVenta() == null ? null : TipoVentaCurso.valueOf(c.getTipoVenta()),
                c.isDestacado(),
                c.getPrecioRegular(),
                c.getPrecioPromocional(),
                c.getPromocionInicioEn(),
                c.getPromocionFinEn(),
                c.getFechaInicio(),
                c.getFechaFin(),
                c.getFechaCierreMatricula(),
                c.getCupoMaximo(),
                c.getHorasAcademicas(),
                c.getVigenciaAccesoDias(),
                List.of(c.getBeneficios()),
                c.getEstadoCurso().getCodigo(),
                c.getEstadoCurso().getNombre(),
                c.getPublicadoEn() != null,
                docentes,
                firmantesCurso,
                c.getCreadoEn());
    }
}
