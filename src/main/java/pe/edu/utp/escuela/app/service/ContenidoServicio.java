package pe.edu.utp.escuela.app.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.dto.ActualizarMaterialPeticion;
import pe.edu.utp.escuela.app.dto.ArchivoGuardado;
import pe.edu.utp.escuela.app.dto.CrearLeccionPeticion;
import pe.edu.utp.escuela.app.dto.CrearMaterialEnlacePeticion;
import pe.edu.utp.escuela.app.dto.CrearModuloPeticion;
import pe.edu.utp.escuela.app.dto.LeccionRespuesta;
import pe.edu.utp.escuela.app.dto.MaterialRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloDisponibleRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloRespuesta;
import pe.edu.utp.escuela.app.dto.OrdenPeticion;
import pe.edu.utp.escuela.app.dto.OrigenRecurso;
import pe.edu.utp.escuela.app.dto.RecursoRespuesta;
import pe.edu.utp.escuela.app.dto.TipoLeccion;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Recurso;
import pe.edu.utp.escuela.app.entity.TipoMaterial;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.RecursoRepositorio;
import pe.edu.utp.escuela.app.repository.TipoMaterialRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-011 — Organizar el contenido de un curso: módulos, lecciones y materiales. No incluye
 * exámenes (HU-013) ni la gestión operativa de sesiones en vivo (HU-012); la copia de un módulo
 * tampoco copia exámenes todavía porque esa historia no existe aún. */
@Service
@RequiredArgsConstructor
public class ContenidoServicio {

    private final CursoRepositorio cursos;
    private final ModuloRepositorio modulos;
    private final LeccionRepositorio lecciones;
    private final MaterialLeccionRepositorio materiales;
    private final RecursoRepositorio recursos;
    private final TipoMaterialRepositorio tiposMaterial;
    private final ArchivoAlmacenamientoServicio almacenamiento;
    private final CurrentUserService currentUserService;
    private final TextNormalizer textos;

    // ---------------------------------------------------------------- Lectura --

    @Transactional(readOnly = true)
    public List<ModuloRespuesta> obtenerEstructura(Long cursoId) {
        exigirAdministrador();
        buscarCursoOLanzar(cursoId);
        List<Modulo> modulosDelCurso = modulos.findByCurso_IdOrderByOrdenAsc(cursoId);
        return construirModulos(modulosDelCurso);
    }

    @Transactional(readOnly = true)
    public List<ModuloDisponibleRespuesta> listarModulosDisponibles(String texto, Long excluirCursoId) {
        exigirAdministrador();
        String termino = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        return modulos.findAll().stream()
                .filter(m -> m.isActivo())
                .filter(m -> excluirCursoId == null || !m.getCurso().getId().equals(excluirCursoId))
                .filter(m -> termino.isEmpty() || m.getTitulo().toLowerCase(Locale.ROOT).contains(termino))
                .map(m -> new ModuloDisponibleRespuesta(
                        m.getId(), m.getTitulo(), m.getCurso().getId(), m.getCurso().getTitulo(),
                        lecciones.countByModulo_Id(m.getId())))
                .limit(15)
                .toList();
    }

    // ---------------------------------------------------------------- Módulos --

    @Transactional
    public ModuloRespuesta crearModulo(Long cursoId, CrearModuloPeticion p) {
        exigirAdministrador();
        Curso curso = buscarCursoOLanzar(cursoId);
        Modulo modulo = new Modulo();
        modulo.setCurso(curso);
        modulo.setTitulo(textos.requireText(p.titulo(), "Título"));
        modulo.setDescripcion(textos.trimToNull(p.descripcion()));
        modulo.setOrden((int) modulos.countByCurso_Id(cursoId) + 1);
        modulo.setActivo(true);
        modulos.saveAndFlush(modulo);
        return mapearModulo(modulo, List.of());
    }

    @Transactional
    public ModuloRespuesta actualizarModulo(Long moduloId, CrearModuloPeticion p) {
        exigirAdministrador();
        Modulo modulo = buscarModuloOLanzar(moduloId);
        modulo.setTitulo(textos.requireText(p.titulo(), "Título"));
        modulo.setDescripcion(textos.trimToNull(p.descripcion()));
        return mapearModulo(modulo, lecciones.findByModulo_IdOrderByOrdenAsc(moduloId));
    }

    @Transactional
    public ModuloRespuesta cambiarActivoModulo(Long moduloId, boolean activo) {
        exigirAdministrador();
        Modulo modulo = buscarModuloOLanzar(moduloId);
        modulo.setActivo(activo);
        return mapearModulo(modulo, lecciones.findByModulo_IdOrderByOrdenAsc(moduloId));
    }

    @Transactional
    public List<ModuloRespuesta> reordenarModulos(Long cursoId, OrdenPeticion p) {
        exigirAdministrador();
        buscarCursoOLanzar(cursoId);
        List<Modulo> actuales = modulos.findByCurso_IdOrderByOrdenAsc(cursoId);
        Map<Long, Modulo> porId = validarOrdenCompleto(actuales, Modulo::getId, p.ids(), "módulos");

        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(-(i + 1));
        }
        modulos.flush();
        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(i + 1);
        }
        modulos.flush();

        return construirModulos(modulos.findByCurso_IdOrderByOrdenAsc(cursoId));
    }

    // ---------------------------------------------------------------- Lecciones --

    @Transactional
    public LeccionRespuesta crearLeccion(Long moduloId, CrearLeccionPeticion p) {
        exigirAdministrador();
        Modulo modulo = buscarModuloOLanzar(moduloId);
        Leccion leccion = new Leccion();
        leccion.setModulo(modulo);
        leccion.setTitulo(textos.requireText(p.titulo(), "Título"));
        leccion.setDescripcion(textos.trimToNull(p.descripcion()));
        leccion.setOrden((int) lecciones.countByModulo_Id(moduloId) + 1);
        leccion.setTipo(p.tipo().name());
        leccion.setEstado("PROGRAMADA");
        leccion.setEsObligatoria(p.esObligatoria());
        leccion.setEsVistaPrevia(validarVistaPrevia(p.tipo(), p.esVistaPrevia()));
        leccion.setFechaHoraInicio(p.tipo() == TipoLeccion.EN_VIVO ? p.fechaHoraInicio() : null);
        leccion.setFechaHoraFin(p.tipo() == TipoLeccion.EN_VIVO ? p.fechaHoraFin() : null);
        leccion.setActivo(true);
        lecciones.saveAndFlush(leccion);
        return mapearLeccion(leccion, List.of());
    }

    @Transactional
    public LeccionRespuesta actualizarLeccion(Long leccionId, CrearLeccionPeticion p) {
        exigirAdministrador();
        Leccion leccion = buscarLeccionOLanzar(leccionId);
        leccion.setTitulo(textos.requireText(p.titulo(), "Título"));
        leccion.setDescripcion(textos.trimToNull(p.descripcion()));
        leccion.setTipo(p.tipo().name());
        leccion.setEsObligatoria(p.esObligatoria());
        leccion.setEsVistaPrevia(validarVistaPrevia(p.tipo(), p.esVistaPrevia()));
        leccion.setFechaHoraInicio(p.tipo() == TipoLeccion.EN_VIVO ? p.fechaHoraInicio() : null);
        leccion.setFechaHoraFin(p.tipo() == TipoLeccion.EN_VIVO ? p.fechaHoraFin() : null);
        return mapearLeccion(leccion, materiales.findByLeccion_IdOrderByOrdenAsc(leccionId));
    }

    @Transactional
    public LeccionRespuesta cambiarActivoLeccion(Long leccionId, boolean activo) {
        exigirAdministrador();
        Leccion leccion = buscarLeccionOLanzar(leccionId);
        leccion.setActivo(activo);
        return mapearLeccion(leccion, materiales.findByLeccion_IdOrderByOrdenAsc(leccionId));
    }

    @Transactional
    public List<LeccionRespuesta> reordenarLecciones(Long moduloId, OrdenPeticion p) {
        exigirAdministrador();
        buscarModuloOLanzar(moduloId);
        List<Leccion> actuales = lecciones.findByModulo_IdOrderByOrdenAsc(moduloId);
        Map<Long, Leccion> porId = validarOrdenCompleto(actuales, Leccion::getId, p.ids(), "lecciones");

        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(-(i + 1));
        }
        lecciones.flush();
        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(i + 1);
        }
        lecciones.flush();

        return lecciones.findByModulo_IdOrderByOrdenAsc(moduloId).stream()
                .map(l -> mapearLeccion(l, materiales.findByLeccion_IdOrderByOrdenAsc(l.getId())))
                .toList();
    }

    // ---------------------------------------------------------------- Materiales --

    @Transactional
    public MaterialRespuesta crearMaterialEnlace(Long leccionId, CrearMaterialEnlacePeticion p) {
        exigirAdministrador();
        Leccion leccion = buscarLeccionOLanzar(leccionId);
        TipoMaterial tipoMaterial = tiposMaterial.findById(p.tipoMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de material ya no existe."));
        if (p.origen() == OrigenRecurso.SUBIDO) {
            throw new BusinessValidationException("Usa la carga de archivo para el origen SUBIDO.");
        }
        if (p.origen() == OrigenRecurso.YOUTUBE && !"VIDEO".equals(tipoMaterial.getCodigo())) {
            throw new BusinessValidationException("El origen YouTube solo aplica a materiales de tipo Video.");
        }

        Recurso recurso = new Recurso();
        recurso.setTipoMaterial(tipoMaterial);
        recurso.setOrigen(p.origen().name());
        recurso.setTipo(p.origen() == OrigenRecurso.YOUTUBE ? "VIDEO" : "ENLACE");
        recurso.setReferencia(textos.requireText(p.referencia(), "Referencia"));
        recurso.setDuracionSegundos(p.duracionSegundos());
        recurso.setDuracionDetectada(false);
        if (p.origen() == OrigenRecurso.YOUTUBE) {
            recurso.setYoutubeNoListadoConfirmado(Boolean.TRUE.equals(p.youtubeNoListadoConfirmado()));
        }
        recurso.setActivo(true);
        recursos.saveAndFlush(recurso);

        return guardarMaterial(leccion, recurso, p.titulo(), p.permiteDescarga());
    }

    @Transactional
    public MaterialRespuesta subirMaterialArchivo(
            Long leccionId, MultipartFile archivo, String titulo, Long tipoMaterialId, boolean permiteDescarga) {
        exigirAdministrador();
        Leccion leccion = buscarLeccionOLanzar(leccionId);
        TipoMaterial tipoMaterial = tiposMaterial.findById(tipoMaterialId)
                .orElseThrow(() -> new ResourceNotFoundException("El tipo de material ya no existe."));

        ArchivoGuardado guardado = almacenamiento.guardar(archivo, tipoMaterial, leccion.getModulo().getCurso().getId());

        Recurso recurso = new Recurso();
        recurso.setTipoMaterial(tipoMaterial);
        recurso.setOrigen(OrigenRecurso.SUBIDO.name());
        recurso.setTipo("VIDEO".equals(tipoMaterial.getCodigo()) ? "VIDEO" : "ARCHIVO");
        recurso.setReferencia(guardado.referencia());
        recurso.setNombreArchivo(guardado.nombreOriginal());
        recurso.setTipoMime(guardado.tipoMime());
        recurso.setTamanoBytes(guardado.tamanoBytes());
        recurso.setDuracionDetectada(false);
        recurso.setActivo(true);
        recursos.saveAndFlush(recurso);

        String tituloFinal = textos.trimToNull(titulo);
        return guardarMaterial(leccion, recurso, tituloFinal != null ? tituloFinal : guardado.nombreOriginal(), permiteDescarga);
    }

    private MaterialRespuesta guardarMaterial(Leccion leccion, Recurso recurso, String titulo, boolean permiteDescarga) {
        MaterialLeccion material = new MaterialLeccion();
        material.setLeccion(leccion);
        material.setRecurso(recurso);
        material.setTitulo(textos.requireText(titulo, "Título"));
        material.setOrden((int) materiales.countByLeccion_Id(leccion.getId()) + 1);
        material.setPermiteDescarga(permiteDescarga);
        material.setActivo(true);
        materiales.saveAndFlush(material);
        return mapearMaterial(material);
    }

    @Transactional
    public MaterialRespuesta actualizarMaterial(Long materialId, ActualizarMaterialPeticion p) {
        exigirAdministrador();
        MaterialLeccion material = buscarMaterialOLanzar(materialId);
        material.setTitulo(textos.requireText(p.titulo(), "Título"));
        material.setPermiteDescarga(p.permiteDescarga());

        Recurso recurso = material.getRecurso();
        if (!OrigenRecurso.SUBIDO.name().equals(recurso.getOrigen())) {
            recurso.setReferencia(textos.requireText(p.referencia(), "URL"));
            if (OrigenRecurso.YOUTUBE.name().equals(recurso.getOrigen())) {
                recurso.setYoutubeNoListadoConfirmado(Boolean.TRUE.equals(p.youtubeNoListadoConfirmado()));
            }
        }
        return mapearMaterial(material);
    }

    @Transactional
    public MaterialRespuesta cambiarActivoMaterial(Long materialId, boolean activo) {
        exigirAdministrador();
        MaterialLeccion material = buscarMaterialOLanzar(materialId);
        material.setActivo(activo);
        return mapearMaterial(material);
    }

    @Transactional
    public List<MaterialRespuesta> reordenarMateriales(Long leccionId, OrdenPeticion p) {
        exigirAdministrador();
        buscarLeccionOLanzar(leccionId);
        List<MaterialLeccion> actuales = materiales.findByLeccion_IdOrderByOrdenAsc(leccionId);
        Map<Long, MaterialLeccion> porId = validarOrdenCompleto(actuales, MaterialLeccion::getId, p.ids(), "materiales");

        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(-(i + 1));
        }
        materiales.flush();
        for (int i = 0; i < p.ids().size(); i++) {
            porId.get(p.ids().get(i)).setOrden(i + 1);
        }
        materiales.flush();

        return materiales.findByLeccion_IdOrderByOrdenAsc(leccionId).stream().map(this::mapearMaterial).toList();
    }

    // ---------------------------------------------------------------- Copiar módulo --

    /** Copia módulo, lecciones y materiales; los materiales de la copia reutilizan el mismo
     * recurso físico (no se duplica el archivo). No copia exámenes: HU-013 todavía no existe. */
    @Transactional
    public ModuloRespuesta copiarModulo(Long cursoId, Long moduloOrigenId) {
        exigirAdministrador();
        Curso cursoDestino = buscarCursoOLanzar(cursoId);
        Modulo origen = buscarModuloOLanzar(moduloOrigenId);

        Modulo copia = new Modulo();
        copia.setCurso(cursoDestino);
        copia.setModuloOrigenId(origen.getId());
        copia.setTitulo(origen.getTitulo());
        copia.setDescripcion(origen.getDescripcion());
        copia.setOrden((int) modulos.countByCurso_Id(cursoId) + 1);
        copia.setActivo(true);
        modulos.saveAndFlush(copia);

        List<Leccion> leccionesOrigen = lecciones.findByModulo_IdOrderByOrdenAsc(origen.getId());
        for (Leccion leccionOrigen : leccionesOrigen) {
            Leccion copiaLeccion = new Leccion();
            copiaLeccion.setModulo(copia);
            copiaLeccion.setLeccionOrigenId(leccionOrigen.getId());
            copiaLeccion.setTitulo(leccionOrigen.getTitulo());
            copiaLeccion.setDescripcion(leccionOrigen.getDescripcion());
            copiaLeccion.setOrden(leccionOrigen.getOrden());
            copiaLeccion.setTipo(leccionOrigen.getTipo());
            copiaLeccion.setEstado("PROGRAMADA");
            copiaLeccion.setEsObligatoria(leccionOrigen.isEsObligatoria());
            copiaLeccion.setEsVistaPrevia(leccionOrigen.isEsVistaPrevia());
            copiaLeccion.setFechaHoraInicio(leccionOrigen.getFechaHoraInicio());
            copiaLeccion.setFechaHoraFin(leccionOrigen.getFechaHoraFin());
            copiaLeccion.setActivo(true);
            lecciones.saveAndFlush(copiaLeccion);

            for (MaterialLeccion materialOrigen : materiales.findByLeccion_IdOrderByOrdenAsc(leccionOrigen.getId())) {
                MaterialLeccion copiaMaterial = new MaterialLeccion();
                copiaMaterial.setLeccion(copiaLeccion);
                copiaMaterial.setRecurso(materialOrigen.getRecurso());
                copiaMaterial.setTitulo(materialOrigen.getTitulo());
                copiaMaterial.setOrden(materialOrigen.getOrden());
                copiaMaterial.setPermiteDescarga(materialOrigen.isPermiteDescarga());
                copiaMaterial.setActivo(true);
                materiales.saveAndFlush(copiaMaterial);
            }
        }

        return construirModulos(List.of(copia)).get(0);
    }

    // ---------------------------------------------------------------- Soporte --

    /** "Vista previa pública definida por lección, nunca por examen ni sesión en vivo": una
     * lección EN_VIVO nunca puede marcarse como vista previa. */
    private boolean validarVistaPrevia(TipoLeccion tipo, boolean esVistaPrevia) {
        if (esVistaPrevia && tipo == TipoLeccion.EN_VIVO) {
            throw new BusinessValidationException("Una sesión en vivo no puede marcarse como vista previa pública.");
        }
        return esVistaPrevia;
    }

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private Curso buscarCursoOLanzar(Long cursoId) {
        return cursos.findById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso ya no existe."));
    }

    private Modulo buscarModuloOLanzar(Long moduloId) {
        return modulos.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("El módulo ya no existe."));
    }

    private Leccion buscarLeccionOLanzar(Long leccionId) {
        return lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("La lección ya no existe."));
    }

    private MaterialLeccion buscarMaterialOLanzar(Long materialId) {
        return materiales.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("El material ya no existe."));
    }

    private <T> Map<Long, T> validarOrdenCompleto(
            List<T> actuales, Function<T, Long> idDe, List<Long> idsSolicitados, String etiqueta) {
        Map<Long, T> porId = actuales.stream().collect(Collectors.toMap(idDe, x -> x));
        if (idsSolicitados.size() != porId.size() || !porId.keySet().containsAll(idsSolicitados)
                || idsSolicitados.stream().distinct().count() != idsSolicitados.size()) {
            throw new BusinessValidationException("La lista de orden debe incluir exactamente todos los " + etiqueta + " actuales, sin repetir.");
        }
        return porId;
    }

    private List<ModuloRespuesta> construirModulos(List<Modulo> modulosDelCurso) {
        List<Long> moduloIds = modulosDelCurso.stream().map(Modulo::getId).toList();
        List<Leccion> todasLecciones = moduloIds.isEmpty()
                ? List.of()
                : lecciones.findByModulo_IdInOrderByModulo_IdAscOrdenAsc(moduloIds);
        List<Long> leccionIds = todasLecciones.stream().map(Leccion::getId).toList();
        List<MaterialLeccion> todosMateriales = leccionIds.isEmpty()
                ? List.of()
                : materiales.findByLeccion_IdInOrderByLeccion_IdAscOrdenAsc(leccionIds);

        Map<Long, List<MaterialLeccion>> materialesPorLeccion = todosMateriales.stream()
                .collect(Collectors.groupingBy(m -> m.getLeccion().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<Leccion>> leccionesPorModulo = todasLecciones.stream()
                .collect(Collectors.groupingBy(l -> l.getModulo().getId(), LinkedHashMap::new, Collectors.toList()));

        return modulosDelCurso.stream()
                .sorted(Comparator.comparing(Modulo::getOrden))
                .map(m -> mapearModulo(m, leccionesPorModulo.getOrDefault(m.getId(), List.of()), materialesPorLeccion))
                .toList();
    }

    private ModuloRespuesta mapearModulo(Modulo m, List<Leccion> leccionesDelModulo) {
        Map<Long, List<MaterialLeccion>> materialesPorLeccion = leccionesDelModulo.stream()
                .collect(Collectors.toMap(Leccion::getId, l -> materiales.findByLeccion_IdOrderByOrdenAsc(l.getId())));
        return mapearModulo(m, leccionesDelModulo, materialesPorLeccion);
    }

    private ModuloRespuesta mapearModulo(
            Modulo m, List<Leccion> leccionesDelModulo, Map<Long, List<MaterialLeccion>> materialesPorLeccion) {
        List<LeccionRespuesta> leccionesRespuesta = leccionesDelModulo.stream()
                .sorted(Comparator.comparing(Leccion::getOrden))
                .map(l -> mapearLeccion(l, materialesPorLeccion.getOrDefault(l.getId(), List.of())))
                .toList();
        return new ModuloRespuesta(
                m.getId(), m.getTitulo(), m.getDescripcion(), m.getOrden(), m.isActivo(),
                m.getModuloOrigenId(), leccionesRespuesta);
    }

    private LeccionRespuesta mapearLeccion(Leccion l, List<MaterialLeccion> materialesDeLeccion) {
        List<MaterialRespuesta> materialesRespuesta = materialesDeLeccion.stream()
                .sorted(Comparator.comparing(MaterialLeccion::getOrden))
                .map(this::mapearMaterial)
                .toList();
        return new LeccionRespuesta(
                l.getId(), l.getTitulo(), l.getDescripcion(), l.getOrden(), l.getTipo(),
                l.isEsObligatoria(), l.isEsVistaPrevia(), l.getFechaHoraInicio(), l.getFechaHoraFin(),
                l.isActivo(), l.getLeccionOrigenId(), materialesRespuesta);
    }

    private MaterialRespuesta mapearMaterial(MaterialLeccion m) {
        Recurso r = m.getRecurso();
        RecursoRespuesta recursoRespuesta = new RecursoRespuesta(
                r.getId(), r.getTipo(), r.getOrigen(), r.getReferencia(), r.getNombreArchivo(),
                r.getTipoMime(), r.getTamanoBytes(), r.getDuracionSegundos(), r.isDuracionDetectada(),
                r.getYoutubeNoListadoConfirmado(), r.getTipoMaterial().getCodigo(), r.getTipoMaterial().getNombre());
        return new MaterialRespuesta(m.getId(), m.getTitulo(), m.getOrden(), m.isPermiteDescarga(), m.isActivo(), recursoRespuesta);
    }
}
