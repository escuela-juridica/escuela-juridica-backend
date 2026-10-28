package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CursoTarjetaFila;
import pe.edu.utp.escuela.app.dto.CursoTarjetaResponse;
import pe.edu.utp.escuela.app.dto.DocenteBreveResponse;
import pe.edu.utp.escuela.app.dto.DocenteCursoFila;
import pe.edu.utp.escuela.app.dto.EstadoComercialResponse;
import pe.edu.utp.escuela.app.dto.FiltrosCursoResponse;
import pe.edu.utp.escuela.app.dto.OpcionFiltroResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.repository.CategoriaTematicaRepository;
import pe.edu.utp.escuela.app.repository.CursoDocenteRepository;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.TipoCursoRepository;
import pe.edu.utp.escuela.app.service.CourseCommercialStatusService.CourseCommercialData;
import pe.edu.utp.escuela.app.service.CourseCommercialStatusService.CourseCommercialStatus;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private static final int TAMANO_MINIMO = 1;
    private static final int TAMANO_MAXIMO = 50;

    private final CursoRepository cursoRepository;
    private final MatriculaRepository matriculaRepository;
    private final CursoDocenteRepository cursoDocenteRepository;
    private final TipoCursoRepository tipoCursoRepository;
    private final CategoriaTematicaRepository categoriaTematicaRepository;
    private final CourseCommercialStatusService courseCommercialStatusService;
    private final TextNormalizer textNormalizer;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CursoTarjetaResponse> listar(
            String texto, String tipo, String categoria, int pagina, int tamano) {
        int limite = Math.max(TAMANO_MINIMO, Math.min(tamano, TAMANO_MAXIMO));
        Page<CursoTarjetaFila> filas = cursoRepository.buscarPublicados(
                normalizarTexto(texto),
                normalizarCodigo(tipo),
                normalizarCodigo(categoria),
                LocalDate.now(clock),
                PageRequest.of(Math.max(pagina, 0), limite));

        if (filas.isEmpty()) {
            return PageResponse.from(List.of(), filas);
        }

        List<Long> cursoIds = filas.getContent().stream().map(CursoTarjetaFila::cursoId).toList();
        Map<Long, Long> activas = matriculaRepository.contarActivas(cursoIds);
        Map<Long, List<DocenteBreveResponse>> docentes = agruparDocentes(cursoIds);

        List<CursoTarjetaResponse> elementos = filas.getContent().stream()
                .map(fila -> mapear(
                        fila,
                        activas.getOrDefault(fila.cursoId(), 0L),
                        docentes.getOrDefault(fila.cursoId(), List.of())))
                .toList();

        return PageResponse.from(elementos, filas);
    }

    @Transactional(readOnly = true)
    public FiltrosCursoResponse filtros() {
        List<OpcionFiltroResponse> tipos = tipoCursoRepository
                .findByActivoTrueOrderByOrdenAscNombreAsc(Pageable.unpaged()).stream()
                .map(tipo -> new OpcionFiltroResponse(tipo.getCodigo(), tipo.getNombre()))
                .toList();
        List<OpcionFiltroResponse> categorias = categoriaTematicaRepository
                .findByActivoTrueOrderByOrdenAscNombreAsc(Pageable.unpaged()).stream()
                .map(categoria -> new OpcionFiltroResponse(
                        categoria.getCodigo(), categoria.getNombre()))
                .toList();
        return new FiltrosCursoResponse(tipos, categorias);
    }

    private Map<Long, List<DocenteBreveResponse>> agruparDocentes(List<Long> cursoIds) {
        return cursoDocenteRepository.buscarDocentesDeCursos(cursoIds).stream()
                .sorted(Comparator.comparing(DocenteCursoFila::orden))
                .collect(Collectors.groupingBy(
                        DocenteCursoFila::cursoId,
                        Collectors.mapping(this::mapearDocente, Collectors.toList())));
    }

    private DocenteBreveResponse mapearDocente(DocenteCursoFila fila) {
        String nombreCompleto = componerNombre(
                fila.nombres(), fila.apellidoPaterno(), fila.apellidoMaterno());
        return new DocenteBreveResponse(
                fila.personaId(),
                nombreCompleto,
                componerIniciales(fila.nombres(), fila.apellidoPaterno()),
                fila.fotoUrl(),
                fila.cargoProfesional());
    }

    private String componerNombre(String nombres, String apellidoPaterno, String apellidoMaterno) {
        StringBuilder completo = new StringBuilder(nombres).append(' ').append(apellidoPaterno);
        if (apellidoMaterno != null && !apellidoMaterno.isBlank()) {
            completo.append(' ').append(apellidoMaterno);
        }
        return completo.toString();
    }

    private String componerIniciales(String nombres, String apellidoPaterno) {
        char inicialNombre = nombres.charAt(0);
        char inicialApellido = apellidoPaterno.charAt(0);
        return ("" + inicialNombre + inicialApellido).toUpperCase(Locale.ROOT);
    }

    private CursoTarjetaResponse mapear(
            CursoTarjetaFila fila, long activas, List<DocenteBreveResponse> docentes) {
        CourseCommercialData datos = new CourseCommercialData(
                fila.modalidad(),
                fila.tipoVenta(),
                fila.estadoCursoCodigo(),
                fila.precioRegular(),
                fila.precioPromocional(),
                fila.promocionInicioEn(),
                fila.promocionFinEn(),
                fila.fechaInicio(),
                fila.fechaCierreMatricula(),
                fila.cupoMaximo());
        CourseCommercialStatus estado = courseCommercialStatusService.calculate(datos, activas);

        return new CursoTarjetaResponse(
                fila.urlAmigable(),
                fila.titulo(),
                fila.descripcion(),
                fila.imagenPortadaUrl(),
                fila.modalidad(),
                fila.tipoVenta(),
                fila.tipoCursoCodigo(),
                fila.tipoCursoNombre(),
                fila.categoriaCodigo(),
                fila.categoriaNombre(),
                fila.destacado(),
                fila.horasAcademicas(),
                docentes,
                new EstadoComercialResponse(
                        estado.code().name(),
                        estado.label(),
                        estado.startDate(),
                        estado.enrollmentAllowed(),
                        estado.currentPrice(),
                        estado.regularPrice(),
                        estado.promotionActive(),
                        estado.action().name()));
    }

    private String normalizarTexto(String valor) {
        String limpio = textNormalizer.trimToNull(valor);
        return limpio == null ? "" : limpio.toLowerCase(Locale.ROOT);
    }

    private String normalizarCodigo(String valor) {
        String limpio = textNormalizer.trimToNull(valor);
        if (limpio == null || "TODOS".equalsIgnoreCase(limpio)) {
            return "";
        }
        return limpio.toUpperCase(Locale.ROOT);
    }
}
