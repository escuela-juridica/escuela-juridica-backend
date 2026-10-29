package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.DashboardResponse;
import pe.edu.utp.escuela.app.dto.DashboardResponse.Barra;
import pe.edu.utp.escuela.app.dto.DashboardResponse.Punto;
import pe.edu.utp.escuela.app.dto.DashboardResponse.PuntoMonto;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository.ActivacionRow;
import pe.edu.utp.escuela.app.repository.PagoRepository;
import pe.edu.utp.escuela.app.repository.PagoRepository.ConfirmadoRow;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-046 — Dashboard administrativo: exactamente cuatro gráficos que comparten rango, curso y modalidad. Las reglas de
 * conteo son las de los reportes: una matrícula cuenta una sola vez (la que llegó a ACTIVA) y un pago confirmado
 * también (APROBADO y REGISTRADO_MANUAL con monto mayor que cero), de modo que cada cifra se puede comprobar con el
 * detalle filtrado del reporte correspondiente. No es contabilidad ni una bandeja de tareas.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<String> MODALIDADES = Set.of("VIRTUAL", "EN_VIVO", "HIBRIDO");
    private static final int DIAS_MAXIMOS_RANGO = 3660;
    private static final int CURSOS_VISIBLES = 10;
    private static final int LOTE_IDS = 1000;
    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    /** Formas de ingreso del gráfico, en orden fijo (cada una conserva su color aunque otra quede en cero). */
    private static final Map<String, String> FORMAS = new LinkedHashMap<>();

    static {
        FORMAS.put("PAGO_EN_LINEA", "Pago en línea");
        FORMAS.put("REGISTRADO_MANUAL", "Pago manual registrado");
        FORMAS.put("EXONERADO", "Exonerado");
        FORMAS.put("GRATUITA", "Curso gratuito");
    }

    private final MatriculaRepository matriculas;
    private final PagoRepository pagos;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse consultar(LocalDate desde, LocalDate hasta, Long cursoId, String modalidad) {
        exigirAdministrador();
        LocalDate hoy = LocalDate.now(clock.withZone(LIMA));
        LocalDate inicio = desde != null ? desde : LocalDate.of(hasta != null ? hasta.getYear() : hoy.getYear(), 1, 1);
        LocalDate fin = hasta != null ? hasta : LocalDate.of(desde != null ? desde.getYear() : hoy.getYear(), 12, 31);
        if (fin.isBefore(inicio)) {
            throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > DIAS_MAXIMOS_RANGO) {
            throw new BusinessValidationException("El rango no puede superar 10 años.");
        }
        String mod = modalidad == null ? "" : modalidad.strip().toUpperCase(Locale.ROOT);
        if (!mod.isEmpty() && !MODALIDADES.contains(mod)) {
            throw new BusinessValidationException("La modalidad no es válida.");
        }
        Instant desdeInstante = inicio.atStartOfDay(LIMA).toInstant();
        Instant hastaInstante = fin.plusDays(1).atStartOfDay(LIMA).toInstant();
        boolean porDia = ChronoUnit.DAYS.between(inicio, fin) + 1 <= 31;

        List<ActivacionRow> activadas = matriculas.activacionesParaDashboard(cursoId, mod, desdeInstante, hastaInstante);
        List<ConfirmadoRow> confirmados = pagos.confirmadosParaDashboard(cursoId, mod, desdeInstante, hastaInstante);

        // 1) Evolución de matrículas activadas.
        Map<String, long[]> serie = periodos(inicio, fin, porDia, new long[1]);
        for (ActivacionRow a : activadas) {
            serie.get(clave(a.getFecha(), porDia))[0]++;
        }
        List<Punto> evolucion = serie.entrySet().stream()
                .map(e -> new Punto(e.getKey(), etiqueta(e.getKey(), porDia), e.getValue()[0])).toList();

        return new DashboardResponse(inicio, fin, porDia ? "DIA" : "MES", evolucion, porCurso(activadas),
                formaIngreso(activadas), pagosPorPeriodo(confirmados, inicio, fin, porDia));
    }

    // ------------------------------------------------------------------------------------- gráficos --

    /** 2) Distribución de las matrículas activadas por curso: los más frecuentes y el resto agrupado. */
    private static List<Barra> porCurso(List<ActivacionRow> activadas) {
        Map<Long, long[]> conteo = new LinkedHashMap<>();
        Map<Long, String> titulos = new LinkedHashMap<>();
        for (ActivacionRow a : activadas) {
            conteo.computeIfAbsent(a.getCursoId(), k -> new long[1])[0]++;
            titulos.putIfAbsent(a.getCursoId(), a.getCurso());
        }
        List<Barra> todos = conteo.entrySet().stream()
                .map(e -> new Barra(String.valueOf(e.getKey()), titulos.get(e.getKey()), e.getValue()[0]))
                .sorted(Comparator.comparingLong(Barra::valor).reversed().thenComparing(Barra::etiqueta))
                .collect(Collectors.toCollection(ArrayList::new));
        if (todos.size() <= CURSOS_VISIBLES) {
            return todos;
        }
        List<Barra> visibles = new ArrayList<>(todos.subList(0, CURSOS_VISIBLES));
        long resto = todos.subList(CURSOS_VISIBLES, todos.size()).stream().mapToLong(Barra::valor).sum();
        visibles.add(new Barra("OTROS", "Otros " + (todos.size() - CURSOS_VISIBLES) + " cursos", resto));
        return visibles;
    }

    /** 3) Forma de ingreso de esas mismas matrículas, clasificada con la regla del reporte de matrículas. */
    private List<Barra> formaIngreso(List<ActivacionRow> activadas) {
        Map<String, long[]> conteo = new LinkedHashMap<>();
        FORMAS.keySet().forEach(k -> conteo.put(k, new long[1]));
        if (!activadas.isEmpty()) {
            List<Long> ids = activadas.stream().map(ActivacionRow::getId).toList();
            // Por lotes: una lista de miles de ids superaría el límite de parámetros de una consulta.
            Map<Long, List<Pago>> pagosPorMatricula = new java.util.HashMap<>();
            for (int i = 0; i < ids.size(); i += LOTE_IDS) {
                pagos.findByMatricula_IdIn(ids.subList(i, Math.min(i + LOTE_IDS, ids.size()))).forEach(
                        p -> pagosPorMatricula.computeIfAbsent(p.getMatricula().getId(), k -> new ArrayList<>()).add(p));
            }
            for (ActivacionRow a : activadas) {
                List<Pago> delaMatricula = pagosPorMatricula.get(a.getId());
                String forma = delaMatricula == null ? a.getForma() : MatriculaService.origenReporte(delaMatricula);
                long[] c = conteo.get(forma);
                if (c != null) {
                    c[0]++;
                }
            }
        }
        return conteo.entrySet().stream().map(e -> new Barra(e.getKey(), FORMAS.get(e.getKey()), e.getValue()[0])).toList();
    }

    /** 4) Importe de los pagos confirmados por periodo. */
    private static List<PuntoMonto> pagosPorPeriodo(List<ConfirmadoRow> confirmados, LocalDate inicio, LocalDate fin,
            boolean porDia) {
        Map<String, BigDecimal[]> serie = periodos(inicio, fin, porDia, new BigDecimal[] {BigDecimal.ZERO.setScale(2)});
        for (ConfirmadoRow p : confirmados) {
            BigDecimal[] acumulado = serie.get(clave(p.getFecha(), porDia));
            acumulado[0] = acumulado[0].add(p.getImporte());
        }
        return serie.entrySet().stream()
                .map(e -> new PuntoMonto(e.getKey(), etiqueta(e.getKey(), porDia), e.getValue()[0])).toList();
    }

    // ----------------------------------------------------------------------------------------- tiempo --

    /** Todos los periodos del rango, aunque queden en cero, para que la serie sea continua. */
    private static <T> Map<String, T[]> periodos(LocalDate inicio, LocalDate fin, boolean porDia, T[] molde) {
        Map<String, T[]> mapa = new LinkedHashMap<>();
        if (porDia) {
            for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
                mapa.put(d.toString(), molde.clone());
            }
        } else {
            for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fin)); m = m.plusMonths(1)) {
                mapa.put(m.toString(), molde.clone());
            }
        }
        return mapa;
    }

    private static Map<String, long[]> periodos(LocalDate inicio, LocalDate fin, boolean porDia, long[] molde) {
        Map<String, long[]> mapa = new LinkedHashMap<>();
        if (porDia) {
            for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
                mapa.put(d.toString(), molde.clone());
            }
        } else {
            for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fin)); m = m.plusMonths(1)) {
                mapa.put(m.toString(), molde.clone());
            }
        }
        return mapa;
    }

    private static String clave(Instant instante, boolean porDia) {
        LocalDate fecha = instante.atZone(LIMA).toLocalDate();
        return porDia ? fecha.toString() : YearMonth.from(fecha).toString();
    }

    private static String etiqueta(String periodo, boolean porDia) {
        if (porDia) {
            LocalDate d = LocalDate.parse(periodo);
            return d.getDayOfMonth() + " " + MESES[d.getMonthValue() - 1];
        }
        YearMonth m = YearMonth.parse(periodo);
        return MESES[m.getMonthValue() - 1] + " " + m.getYear();
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
