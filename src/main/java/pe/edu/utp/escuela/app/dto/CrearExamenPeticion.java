package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code moduloId}: obligatorio cuando {@code finalidad=MODULO}, debe ser nulo cuando
 * {@code finalidad=FINAL}. {@code maximoIntentos}/{@code tiempoLimiteMinutos}: nulo significa sin
 * límite. {@code fechaHabilitacion}: no admitida en cursos VIRTUAL. */
public record CrearExamenPeticion(
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
        @Min(1) Integer diasRevision) {
}
