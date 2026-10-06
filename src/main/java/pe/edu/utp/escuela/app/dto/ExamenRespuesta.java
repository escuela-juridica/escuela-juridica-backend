package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.util.List;

public record ExamenRespuesta(
        Long id,
        Long cursoId,
        Long moduloId,
        String moduloTitulo,
        String titulo,
        String descripcion,
        String tipo,
        String finalidad,
        int orden,
        Integer maximoIntentos,
        Integer tiempoLimiteMinutos,
        boolean barajarPreguntas,
        boolean barajarOpciones,
        String mostrarRespuestas,
        Instant fechaHabilitacion,
        boolean bloqueaSiguienteModulo,
        int diasRevision,
        boolean activo,
        Long examenOrigenId,
        List<PreguntaRespuesta> preguntas) {
}
