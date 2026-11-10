package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CrearExamenRequest(
        Long moduloId,
        @NotBlank @Size(max = 220) String titulo,
        String descripcion,
        @NotNull TipoExamen tipo,
        @NotNull FinalidadExamen finalidad,
        @Min(1) Integer maximoIntentos,
        @Min(1) Integer tiempoLimiteMinutos,
        boolean barajarPreguntas,
        boolean barajarOpciones,
        @NotNull MostrarRespuestas mostrarRespuestas,
        Instant fechaHabilitacion,
        boolean bloqueaSiguienteModulo,
        @Min(1) Integer diasRevision,
        ModoNavegacionExamen modoNavegacion,
        Boolean permiteOmitirPreguntas) {

    public CrearExamenRequest(
            Long moduloId,
            String titulo,
            String descripcion,
            TipoExamen tipo,
            FinalidadExamen finalidad,
            Integer maximoIntentos,
            Integer tiempoLimiteMinutos,
            boolean barajarPreguntas,
            boolean barajarOpciones,
            MostrarRespuestas mostrarRespuestas,
            Instant fechaHabilitacion,
            boolean bloqueaSiguienteModulo,
            Integer diasRevision) {
        this(moduloId, titulo, descripcion, tipo, finalidad, maximoIntentos, tiempoLimiteMinutos, barajarPreguntas,
                barajarOpciones, mostrarRespuestas, fechaHabilitacion, bloqueaSiguienteModulo, diasRevision,
                ModoNavegacionExamen.LIBRE, Boolean.FALSE);
    }
}
