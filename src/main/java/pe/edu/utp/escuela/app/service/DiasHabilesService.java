package pe.edu.utp.escuela.app.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calendario de días hábiles del Perú: se excluyen sábados, domingos y feriados oficiales. Los feriados
 * fijos y Semana Santa se calculan; los que el Gobierno decrete cada año (días no laborables o
 * feriados extraordinarios) se agregan con {@code esejur.feriados-adicionales} (fechas ISO separadas por comas).
 */
@Service
public class DiasHabilesService {

    private static final Set<MonthDay> FERIADOS_FIJOS = Set.of(
            MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(6, 7), MonthDay.of(6, 29), MonthDay.of(7, 23),
            MonthDay.of(7, 28), MonthDay.of(7, 29), MonthDay.of(8, 6), MonthDay.of(8, 30), MonthDay.of(10, 8),
            MonthDay.of(11, 1), MonthDay.of(12, 8), MonthDay.of(12, 9), MonthDay.of(12, 25));

    private final Set<LocalDate> adicionales = new HashSet<>();

    public DiasHabilesService(@Value("${esejur.feriados-adicionales:}") String adicionalesConfigurados) {
        for (String texto : adicionalesConfigurados.split(",")) {
            if (!texto.isBlank()) {
                adicionales.add(LocalDate.parse(texto.strip()));
            }
        }
    }

    public boolean esHabil(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !esFeriado(fecha);
    }

    public boolean esFeriado(LocalDate fecha) {
        if (adicionales.contains(fecha) || FERIADOS_FIJOS.contains(MonthDay.from(fecha))) {
            return true;
        }
        LocalDate pascua = pascua(fecha.getYear());
        return fecha.equals(pascua.minusDays(3)) || fecha.equals(pascua.minusDays(2));
    }

    /**
     * Fecha máxima de respuesta: el día hábil posterior a la presentación es el día 1 y el plazo
     * corre hasta el día {@code diasHabiles}. Presentar en un día no hábil no adelanta el conteo.
     */
    public LocalDate sumarDiasHabiles(LocalDate presentacion, int diasHabiles) {
        LocalDate fecha = presentacion;
        int contados = 0;
        while (contados < diasHabiles) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha)) {
                contados++;
            }
        }
        return fecha;
    }

    /**
     * Días hábiles que faltan para {@code limite} contando desde mañana (0 si vence hoy o ya venció).
     * HU-040 lo usa para resaltar los casos próximos a vencer sin cerrarlos.
     */
    public int diasHabilesHasta(LocalDate hoy, LocalDate limite) {
        int dias = 0;
        for (LocalDate fecha = hoy.plusDays(1); !fecha.isAfter(limite); fecha = fecha.plusDays(1)) {
            if (esHabil(fecha)) {
                dias++;
            }
        }
        return dias;
    }

    /** Domingo de Pascua (algoritmo gregoriano anónimo). */
    static LocalDate pascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
