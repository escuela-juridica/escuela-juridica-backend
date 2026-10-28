package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * HU-028 / HU-029 — Ficha de un examen para el alumno: reglas, intentos y qué puede hacer ahora.
 *
 * <p>{@code intentosMaximos} y {@code intentosRestantes} son {@code null} cuando los intentos son
 * ilimitados. {@code motivoNoPuedeIniciar} explica por qué no hay botón de inicio (examen aún no
 * habilitado, intento pendiente de revisión, intentos agotados, certificado emitido).
 * {@code soloLectura} es cierto en un curso cancelado: se consulta, pero no se rinde.
 */
public record ExamenFichaRespuesta(
        Long id,
        Long cursoId,
        String titulo,
        String descripcion,
        String tipo,
        String finalidad,
        String modulo,
        int totalPreguntas,
        BigDecimal puntajeTotal,
        boolean tieneRespuestaAbierta,
        Integer tiempoLimiteMinutos,
        Integer intentosMaximos,
        Integer intentosUsados,
        Integer intentosRestantes,
        String mostrarRespuestas,
        BigDecimal notaMinima,
        boolean afectaCertificacion,
        boolean bloqueaSiguienteModulo,
        boolean habilitado,
        boolean puedeIniciar,
        String motivoNoPuedeIniciar,
        boolean agotadoSinAprobar,
        boolean soloLectura,
        Long intentoEnCursoId,
        boolean aprobado,
        BigDecimal mejorNota,
        List<IntentoResumenRespuesta> intentos) {

    public ExamenFichaRespuesta {
        intentos = List.copyOf(intentos);
    }
}
