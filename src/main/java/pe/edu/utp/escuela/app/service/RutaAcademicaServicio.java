package pe.edu.utp.escuela.app.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ExamenAulaRespuesta;
import pe.edu.utp.escuela.app.dto.NotaExamenFila;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.repository.ExamenRepositorio;
import pe.edu.utp.escuela.app.repository.IntentoExamenRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;

/**
 * HU-025 — Ruta académica: única fuente de verdad del orden del curso y de qué lecciones están
 * bloqueadas y por qué. La consumen el aula (para pintar el temario) y el registro de progreso
 * (para no dejar completar una lección bloqueada llamando a la API directo), de modo que el
 * índice, el botón "Siguiente" y el acceso directo respeten siempre el mismo orden y los mismos
 * bloqueos. Nada de esto se guarda como "módulo desbloqueado": se calcula en cada consulta desde
 * las reglas del curso y los resultados reales del alumno.
 *
 * <p>Con la secuencia activa una lección se bloquea por (1) una lección obligatoria previa, no
 * cancelada, sin completar, o (2) un examen calificado de módulo, configurado como bloqueante, sin
 * aprobar en un módulo anterior (si hay varios, todos deben aprobarse). Las prácticas y el examen
 * final nunca bloquean. Con la secuencia apagada nada bloquea. Una sesión CANCELADA queda libre.
 * Lo ya completado siempre queda accesible.
 */
@Service
@RequiredArgsConstructor
public class RutaAcademicaServicio {

    private final ModuloRepositorio modulos;
    private final LeccionRepositorio lecciones;
    private final ProgresoLeccionRepositorio progresoLecciones;
    private final ReglaCursoRepositorio reglasCurso;
    private final ExamenRepositorio examenes;
    private final IntentoExamenRepositorio intentos;

    /** Resultado del cálculo para una matrícula. */
    public record RutaAcademica(
            ReglaCurso regla,
            List<Modulo> modulos,
            Map<Long, List<Leccion>> leccionesPorModulo,
            List<Leccion> globalOrden,
            Map<Long, Boolean> completadas,
            Map<Long, String> bloqueos,
            Map<Long, List<ExamenAulaRespuesta>> examenesPorModulo,
            List<Examen> examenesCalificados,
            Set<Long> examenesAprobados,
            Map<Long, java.math.BigDecimal> mejoresNotas) {

        /** Motivo por el que la lección no puede trabajarse todavía, o {@code null} si está libre. */
        public String motivoBloqueo(Long leccionId) {
            return bloqueos.get(leccionId);
        }
    }

    @Transactional(readOnly = true)
    public RutaAcademica calcular(Matricula matricula) {
        Long cursoId = matricula.getCurso().getId();
        ReglaCurso regla = reglasCurso.findByCurso_Id(cursoId).orElseGet(ReglaCurso::new);

        List<Modulo> modulosCurso = modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(cursoId);
        List<Long> idsModulos = modulosCurso.stream().map(Modulo::getId).toList();
        Map<Long, List<Leccion>> porModulo = idsModulos.isEmpty() ? Map.of()
                : lecciones.buscarActivasDeModulos(idsModulos).stream()
                        .collect(Collectors.groupingBy(l -> l.getModulo().getId()));
        Map<Long, List<Leccion>> leccionesPorModulo = new LinkedHashMap<>();
        List<Leccion> global = new ArrayList<>();
        for (Modulo modulo : modulosCurso) {
            List<Leccion> delModulo = porModulo.getOrDefault(modulo.getId(), List.of());
            leccionesPorModulo.put(modulo.getId(), delModulo);
            global.addAll(delModulo);
        }

        Map<Long, Boolean> completadas = progresoLecciones.findByMatricula_Id(matricula.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLeccion().getId(), ProgresoLeccion::isCompletada, (a, b) -> a));

        List<Examen> activos = examenes.findByCurso_IdOrderByOrdenAsc(cursoId).stream()
                .filter(Examen::isActivo).toList();
        Set<Long> aprobados = new HashSet<>(intentos.idsExamenesAprobados(matricula.getId()));
        Map<Long, java.math.BigDecimal> notas = intentos.mejoresNotas(matricula.getId()).stream()
                .collect(Collectors.toMap(NotaExamenFila::examenId, NotaExamenFila::nota, (a, b) -> a));

        Set<Long> idsModulosActivos = new HashSet<>(idsModulos);
        Map<Long, List<ExamenAulaRespuesta>> examenesPorModulo = new HashMap<>();
        for (Examen examen : activos) {
            if (examen.getModulo() == null || !idsModulosActivos.contains(examen.getModulo().getId())) {
                continue;
            }
            examenesPorModulo.computeIfAbsent(examen.getModulo().getId(), k -> new ArrayList<>())
                    .add(new ExamenAulaRespuesta(examen.getId(), examen.getTitulo(), examen.getTipo(),
                            esBloqueante(examen), aprobados.contains(examen.getId()), notas.get(examen.getId())));
        }

        Map<Long, String> bloqueos = new HashMap<>();
        if (regla.isSecuenciaObligatoria()) {
            Leccion previaObligatoria = null;
            List<String> examenesPendientes = new ArrayList<>();
            for (Modulo modulo : modulosCurso) {
                for (Leccion leccion : leccionesPorModulo.get(modulo.getId())) {
                    if (!Boolean.TRUE.equals(completadas.get(leccion.getId()))) {
                        if (previaObligatoria != null
                                && !Boolean.TRUE.equals(completadas.get(previaObligatoria.getId()))) {
                            bloqueos.put(leccion.getId(),
                                    "Completa la lección anterior: " + previaObligatoria.getTitulo());
                        } else if (!examenesPendientes.isEmpty()) {
                            bloqueos.put(leccion.getId(), motivoExamenes(examenesPendientes));
                        }
                    }
                    // Una sesión CANCELADA se excluye y libera la secuencia: no cuenta como previa.
                    if (leccion.isEsObligatoria() && !"CANCELADA".equals(leccion.getEstado())) {
                        previaObligatoria = leccion;
                    }
                }
                for (ExamenAulaRespuesta examen : examenesPorModulo.getOrDefault(modulo.getId(), List.of())) {
                    if (examen.bloqueaSiguienteModulo() && !examen.aprobado()) {
                        examenesPendientes.add(examen.titulo());
                    }
                }
            }
        }

        List<Examen> calificados = activos.stream().filter(e -> "CALIFICADO".equals(e.getTipo())).toList();
        return new RutaAcademica(regla, modulosCurso, leccionesPorModulo, global, completadas, bloqueos,
                examenesPorModulo, calificados, aprobados, notas);
    }

    /** Solo un examen CALIFICADO de MÓDULO configurado como bloqueante frena el siguiente módulo;
     * la práctica y el examen final no, aunque tengan la bandera encendida. */
    private static boolean esBloqueante(Examen examen) {
        return examen.isBloqueaSiguienteModulo() && "CALIFICADO".equals(examen.getTipo())
                && "MODULO".equals(examen.getFinalidad()) && examen.getModulo() != null;
    }

    private static String motivoExamenes(List<String> titulos) {
        if (titulos.size() == 1) {
            return "Aprueba el examen «" + titulos.get(0) + "» para continuar.";
        }
        return "Aprueba estos exámenes para continuar: "
                + titulos.stream().map(t -> "«" + t + "»").collect(Collectors.joining(", ")) + ".";
    }
}
