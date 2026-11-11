package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExamenFichaResponse(
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
        String modoNavegacion,
        boolean permiteOmitirPreguntas,
        String criterioNota,
        List<IntentoResumenResponse> intentos) {

    public ExamenFichaResponse {
        intentos = List.copyOf(intentos);
    }
}
