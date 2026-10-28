package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Respuesta de un alumno a una pregunta dentro de un intento (HU-028 / HU-029). Las
 * alternativas marcadas viven en {@code respuesta_opcion}; el texto, en las preguntas abiertas. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "respuesta_intento")
public class RespuestaIntento extends RegistroAuditable {

    public static final String NO_APLICA = "NO_APLICA";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String REVISADA = "REVISADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "respuesta_intento_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intento_examen_id", nullable = false)
    private IntentoExamen intentoExamen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private Pregunta pregunta;

    @Column(name = "texto_respuesta", columnDefinition = "text")
    private String textoRespuesta;

    @Column(name = "puntaje_automatico", precision = 6, scale = 2)
    private BigDecimal puntajeAutomatico;

    @Column(name = "puntaje_definitivo", precision = 6, scale = 2)
    private BigDecimal puntajeDefinitivo;

    /** NO_APLICA | PENDIENTE | REVISADA */
    @Column(name = "estado_revision", nullable = false, length = 20)
    private String estadoRevision = NO_APLICA;

    @Column(name = "fecha_limite_revision")
    private Instant fechaLimiteRevision;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "respuesta_opcion",
            joinColumns = @JoinColumn(name = "respuesta_intento_id"),
            inverseJoinColumns = @JoinColumn(name = "opcion_pregunta_id"))
    private Set<OpcionPregunta> opciones = new HashSet<>();
}
