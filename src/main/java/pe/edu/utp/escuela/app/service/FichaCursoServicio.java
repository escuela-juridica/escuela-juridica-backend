package pe.edu.utp.escuela.app.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.DocenteFichaFila;
import pe.edu.utp.escuela.app.dto.DocenteFichaRespuesta;
import pe.edu.utp.escuela.app.dto.DuracionLeccionFila;
import pe.edu.utp.escuela.app.dto.EstadoComercialRespuesta;
import pe.edu.utp.escuela.app.dto.FichaCursoRespuesta;
import pe.edu.utp.escuela.app.dto.LeccionFichaRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloFichaRespuesta;
import pe.edu.utp.escuela.app.dto.RecursoVistaPreviaFila;
import pe.edu.utp.escuela.app.dto.RecursoVistaPreviaRespuesta;
import pe.edu.utp.escuela.app.dto.VistaPreviaRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EntidadCertificadora;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CursoDocenteRepositorio;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.service.CourseCommercialStatusService.CourseCommercialData;
import pe.edu.utp.escuela.app.service.CourseCommercialStatusService.CourseCommercialStatus;

@Service
@RequiredArgsConstructor
public class FichaCursoServicio {

    private final CursoRepositorio cursoRepositorio;
    private final MatriculaRepositorio matriculaRepositorio;
    private final CursoDocenteRepositorio cursoDocenteRepositorio;
    private final ModuloRepositorio moduloRepositorio;
    private final LeccionRepositorio leccionRepositorio;
    private final MaterialLeccionRepositorio materialLeccionRepositorio;
    private final CourseCommercialStatusService courseCommercialStatusService;

    @Transactional(readOnly = true)
    public FichaCursoRespuesta obtener(String urlAmigable) {
        Curso curso = buscarCursoPublicado(urlAmigable);

        long activas = matriculaRepositorio.contarActivas(List.of(curso.getId()))
                .getOrDefault(curso.getId(), 0L);

        List<DocenteFichaRespuesta> docentes = cursoDocenteRepositorio
                .buscarDocentesDelCurso(curso.getId()).stream()
                .sorted(Comparator.comparing(DocenteFichaFila::orden))
                .map(this::mapearDocente)
                .toList();

        List<Modulo> modulos = moduloRepositorio
                .findByCursoIdAndActivoTrueOrderByOrdenAsc(curso.getId());
        List<Long> moduloIds = modulos.stream().map(Modulo::getId).toList();

        List<Leccion> lecciones = moduloIds.isEmpty()
                ? List.of()
                : leccionRepositorio.buscarActivasDeModulos(moduloIds);
        List<Long> leccionIds = lecciones.stream().map(Leccion::getId).toList();

        Map<Long, Integer> duraciones = leccionIds.isEmpty()
                ? Map.of()
                : materialLeccionRepositorio.buscarDuraciones(leccionIds).stream()
                        .filter(fila -> fila.getDuracionSegundos() != null)
                        .collect(Collectors.toMap(
                                DuracionLeccionFila::getLeccionId,
                                DuracionLeccionFila::getDuracionSegundos));

        Map<Long, List<Leccion>> leccionesPorModulo = lecciones.stream()
                .collect(Collectors.groupingBy(
                        leccion -> leccion.getModulo().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<ModuloFichaRespuesta> modulosRespuesta = modulos.stream()
                .map(modulo -> mapearModulo(
                        modulo, leccionesPorModulo.getOrDefault(modulo.getId(), List.of()), duraciones))
                .toList();

        CourseCommercialStatus estado = calcularEstado(curso, activas);
        boolean esVirtual = "VIRTUAL".equals(curso.getModalidad());

        return new FichaCursoRespuesta(
                curso.getId(),
                curso.getUrlAmigable(),
                curso.getTitulo(),
                curso.getDescripcion(),
                curso.getImagenPortadaUrl(),
                curso.getModalidad(),
                curso.getTipoVenta(),
                curso.getTipoCurso() != null ? curso.getTipoCurso().getCodigo() : null,
                curso.getTipoCurso() != null ? curso.getTipoCurso().getNombre() : null,
                curso.getCategoriaTematica() != null ? curso.getCategoriaTematica().getCodigo() : null,
                curso.getCategoriaTematica() != null ? curso.getCategoriaTematica().getNombre() : null,
                entidadNombre(curso.getEntidadCertificadora()),
                entidadLogo(curso.getEntidadCertificadora()),
                curso.getHorasAcademicas(),
                List.of(curso.getBeneficios()),
                curso.getFechaInicio(),
                esVirtual ? null : curso.getFechaFin(),
                docentes,
                modulosRespuesta,
                mapearEstado(estado));
    }

    @Transactional(readOnly = true)
    public VistaPreviaRespuesta obtenerVistaPrevia(String urlAmigable, Long leccionId) {
        List<RecursoVistaPreviaFila> filas = materialLeccionRepositorio
                .buscarVistaPrevia(urlAmigable, leccionId);
        if (filas.isEmpty()) {
            throw new ResourceNotFoundException("No se encontró la vista previa solicitada");
        }
        List<RecursoVistaPreviaRespuesta> materiales = filas.stream()
                .map(fila -> new RecursoVistaPreviaRespuesta(
                        fila.materialLeccionId(), fila.titulo(), fila.orden(),
                        fila.tipoRecurso(), fila.origen(), fila.referencia(),
                        fila.nombreArchivo(), fila.tipoMime(), fila.duracionSegundos(),
                        fila.permiteDescarga()))
                .toList();
        return new VistaPreviaRespuesta(leccionId, materiales);
    }

    private Curso buscarCursoPublicado(String urlAmigable) {
        return cursoRepositorio.buscarFichaPublica(urlAmigable)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el curso"));
    }

    private CourseCommercialStatus calcularEstado(Curso curso, long activas) {
        CourseCommercialData datos = new CourseCommercialData(
                curso.getModalidad(),
                curso.getTipoVenta(),
                curso.getEstadoCurso().getCodigo(),
                curso.getPrecioRegular(),
                curso.getPrecioPromocional(),
                curso.getPromocionInicioEn(),
                curso.getPromocionFinEn(),
                curso.getFechaInicio(),
                curso.getFechaCierreMatricula(),
                curso.getCupoMaximo());
        return courseCommercialStatusService.calculate(datos, activas);
    }

    private EstadoComercialRespuesta mapearEstado(CourseCommercialStatus estado) {
        return new EstadoComercialRespuesta(
                estado.code().name(),
                estado.label(),
                estado.startDate(),
                estado.enrollmentAllowed(),
                estado.currentPrice(),
                estado.regularPrice(),
                estado.promotionActive(),
                estado.action().name());
    }

    private DocenteFichaRespuesta mapearDocente(DocenteFichaFila fila) {
        return new DocenteFichaRespuesta(
                fila.personaId(),
                componerNombre(fila.nombres(), fila.apellidoPaterno(), fila.apellidoMaterno()),
                fila.fotoUrl(),
                fila.cargoProfesional(),
                fila.biografiaProfesional());
    }

    private String componerNombre(String nombres, String apellidoPaterno, String apellidoMaterno) {
        StringBuilder completo = new StringBuilder(nombres).append(' ').append(apellidoPaterno);
        if (apellidoMaterno != null && !apellidoMaterno.isBlank()) {
            completo.append(' ').append(apellidoMaterno);
        }
        return completo.toString();
    }

    private ModuloFichaRespuesta mapearModulo(
            Modulo modulo, List<Leccion> lecciones, Map<Long, Integer> duraciones) {
        List<LeccionFichaRespuesta> leccionesRespuesta = lecciones.stream()
                .map(leccion -> mapearLeccion(leccion, duraciones.get(leccion.getId())))
                .toList();
        return new ModuloFichaRespuesta(
                modulo.getId(), modulo.getTitulo(), modulo.getDescripcion(),
                modulo.getOrden(), leccionesRespuesta);
    }

    private LeccionFichaRespuesta mapearLeccion(Leccion leccion, Integer duracionSegundos) {
        return new LeccionFichaRespuesta(
                leccion.getId(), leccion.getTitulo(), leccion.getOrden(),
                leccion.getTipo(), leccion.getEstado(), leccion.isEsVistaPrevia(),
                leccion.getFechaHoraInicio(), leccion.getFechaHoraFin(), duracionSegundos);
    }

    private String entidadNombre(EntidadCertificadora entidad) {
        return entidad != null ? entidad.getNombre() : null;
    }

    private String entidadLogo(EntidadCertificadora entidad) {
        return entidad != null ? entidad.getLogoUrl() : null;
    }
}
