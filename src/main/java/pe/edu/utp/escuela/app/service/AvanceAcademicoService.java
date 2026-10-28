package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AvanceCertificadoRespuesta;
import pe.edu.utp.escuela.app.dto.CondicionAvanceRespuesta;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.EstadoExamen;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/**
 * Cumplimiento académico de una matrícula frente a las reglas de SU curso: progreso, exámenes
 * calificados, nota y asistencia, cada condición solo si el curso la tiene activa. Lo comparten el
 * aula del alumno (HU-025), la consulta de excepciones (HU-031) y el reporte académico (HU-043),
 * de modo que alumno y administración vean siempre las mismas condiciones, valores y metas.
 *
 * <p>Una condición desactivada no se presenta como pendiente y la asistencia nunca se calcula
 * fuera de lo que el curso exige. Un intento CALIFICADO con respuestas abiertas sin revisar impide
 * dar el curso por finalizado hasta que exista nota definitiva (HU-029).
 */
@Service
@RequiredArgsConstructor
public class AvanceAcademicoService {

    private final RutaAcademicaService rutaAcademica;
    private final SesionAlumnoService sesionAlumno;

    /** Un examen con intento a la espera de calificación humana. */
    public record ExamenEnRevision(Long examenId, String titulo, String tipo, Instant fechaLimite) {
    }

    public record AvanceAcademico(
            RutaAcademica ruta,
            int porcentaje,
            List<CondicionAvanceRespuesta> condiciones,
            boolean cumpleTodo,
            String frase,
            /** Exámenes calificados que el curso exige aprobar (0 si no los exige). */
            int examenesRequeridos,
            int examenesAprobados,
            /** Promedio de las mejores notas con igual peso; {@code null} si el curso no exige exámenes o aún no hay notas. */
            BigDecimal notaFinal,
            /** {@code null} cuando la asistencia no aplica (curso virtual, no exigida o sin sesiones elegibles). */
            Integer asistencia,
            boolean asistenciaExigida,
            List<ExamenEnRevision> enRevision,
            /** Hay un examen CALIFICADO esperando revisión: bloquea finalización y certificado. */
            boolean bloqueadoPorRevision) {

        public AvanceCertificadoRespuesta comoRespuesta() {
            return new AvanceCertificadoRespuesta(condiciones, cumpleTodo, frase);
        }
    }

    @Transactional(readOnly = true)
    public AvanceAcademico calcular(Matricula matricula) {
        return calcular(matricula, rutaAcademica.calcular(matricula));
    }

    @Transactional(readOnly = true)
    public AvanceAcademico calcular(Matricula matricula, RutaAcademica ruta) {
        ReglaCurso regla = ruta.regla();
        int porcentaje = porcentaje(ruta);
        boolean esVirtual = "VIRTUAL".equals(matricula.getCurso().getModalidad());

        List<CondicionAvanceRespuesta> condiciones = new ArrayList<>();
        List<String> faltantes = new ArrayList<>();

        if (regla.isRequiereProgreso()) {
            boolean cumplida = BigDecimal.valueOf(porcentaje).compareTo(regla.getProgresoMinimo()) >= 0;
            condiciones.add(new CondicionAvanceRespuesta("PROGRESO", "Progreso del curso",
                    porcentaje + " %", "mínimo " + numero(regla.getProgresoMinimo()) + " %", cumplida));
            if (!cumplida) {
                faltantes.add("completar al menos " + numero(regla.getProgresoMinimo())
                        + " % de las lecciones obligatorias (llevas " + porcentaje + " %)");
            }
        }

        List<ExamenEnRevision> enRevision = new ArrayList<>();
        for (EstadoExamen estado : ruta.estadoExamenes().values()) {
            if (estado.pendienteRevision() != null && estado.pendienteRevision().getEnviadoEn() != null) {
                Examen examen = estado.examen();
                enRevision.add(new ExamenEnRevision(examen.getId(), examen.getTitulo(), examen.getTipo(),
                        estado.pendienteRevision().getEnviadoEn().plus(examen.getDiasRevision(), ChronoUnit.DAYS)));
            }
        }
        boolean bloqueadoPorRevision = enRevision.stream().anyMatch(e -> "CALIFICADO".equals(e.tipo()));

        int requeridos = 0;
        int aprobados = 0;
        BigDecimal notaFinal = null;
        if (regla.isRequiereExamenes()) {
            List<Examen> calificados = ruta.examenesCalificados();
            requeridos = calificados.size();
            aprobados = (int) calificados.stream().filter(e -> ruta.examenesAprobados().contains(e.getId())).count();
            long pendientes = requeridos - aprobados;
            long enEspera = enRevision.stream().filter(e -> "CALIFICADO".equals(e.tipo())).count();
            boolean examenesCumplidos = pendientes == 0 && !bloqueadoPorRevision;
            condiciones.add(new CondicionAvanceRespuesta("EXAMENES", "Exámenes calificados",
                    aprobados + " de " + requeridos + " aprobados"
                            + (enEspera > 0 ? " · " + enEspera + " en revisión" : ""),
                    "todos aprobados", examenesCumplidos));
            if (pendientes > 0) {
                faltantes.add("aprobar " + pendientes + (pendientes == 1 ? " examen más" : " exámenes más"));
            }
            for (ExamenEnRevision revision : enRevision) {
                if ("CALIFICADO".equals(revision.tipo())) {
                    faltantes.add("esperar la calificación de «" + revision.titulo() + "»");
                }
            }

            List<BigDecimal> mejores = calificados.stream()
                    .map(e -> ruta.mejoresNotas().get(e.getId())).filter(n -> n != null).toList();
            notaFinal = mejores.isEmpty() ? null
                    : mejores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(mejores.size()), 2, RoundingMode.HALF_UP);
            boolean notaCumplida = notaFinal != null && notaFinal.compareTo(regla.getNotaMinima()) >= 0;
            condiciones.add(new CondicionAvanceRespuesta("NOTA", "Nota promedio",
                    notaFinal == null ? "Sin notas aún" : numero(notaFinal),
                    "mínimo " + numero(regla.getNotaMinima()), notaCumplida));
            if (!notaCumplida && notaFinal != null) {
                faltantes.add("subir tu nota promedio a " + numero(regla.getNotaMinima()) + " (llevas " + numero(notaFinal) + ")");
            }
        }

        // La asistencia solo existe en cursos EN_VIVO / HIBRIDO que la exigen: nunca se calcula en VIRTUAL.
        boolean asistenciaExigida = regla.isRequiereAsistencia() && !esVirtual;
        Integer asistencia = null;
        if (asistenciaExigida) {
            asistencia = sesionAlumno.porcentajeAsistencia(matricula.getId());
            // "No aplica" (cero sesiones elegibles) nunca equivale a cumplido (HU-026).
            boolean cumplida = asistencia != null && BigDecimal.valueOf(asistencia).compareTo(regla.getAsistenciaMinima()) >= 0;
            condiciones.add(new CondicionAvanceRespuesta("ASISTENCIA", "Asistencia a sesiones en vivo",
                    asistencia == null ? "No aplica" : asistencia + " %",
                    "mínimo " + numero(regla.getAsistenciaMinima()) + " %", cumplida));
            if (!cumplida) {
                faltantes.add(asistencia == null
                        ? "asistir a las sesiones en vivo (aún no hay sesiones que cuenten)"
                        : "llegar a " + numero(regla.getAsistenciaMinima()) + " % de asistencia (llevas " + asistencia + " %)");
            }
        }

        boolean cumpleTodo = faltantes.isEmpty() && !bloqueadoPorRevision;
        String frase = cumpleTodo
                ? "Cumples todos los requisitos activos para tu certificado."
                : "Te falta: " + String.join("; ", faltantes) + ".";
        return new AvanceAcademico(ruta, porcentaje, condiciones, cumpleTodo, frase, requeridos, aprobados,
                notaFinal, asistencia, asistenciaExigida, enRevision, bloqueadoPorRevision);
    }

    /** Porcentaje de lecciones obligatorias (no canceladas) completadas; si no hay obligatorias,
     * se usan todas las lecciones. Misma fórmula del aula. */
    public static int porcentaje(RutaAcademica ruta) {
        List<Leccion> obligatorias = ruta.globalOrden().stream()
                .filter(l -> l.isEsObligatoria() && !"CANCELADA".equals(l.getEstado())).toList();
        List<Leccion> base = obligatorias.isEmpty() ? ruta.globalOrden() : obligatorias;
        long total = base.size();
        long hechas = base.stream().filter(l -> Boolean.TRUE.equals(ruta.completadas().get(l.getId()))).count();
        return total == 0 ? 0 : (int) Math.round(hechas * 100.0 / total);
    }

    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
