package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "examen")
public class Examen extends RegistroAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "examen_id")
    private Long id;

    @Column(name = "examen_origen_id")
    private Long examenOrigenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modulo_id")
    private Modulo modulo;

    @Column(nullable = false, length = 220)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false, length = 15)
    private String tipo;

    @Column(nullable = false, length = 15)
    private String finalidad;

    @Column(nullable = false)
    private Integer orden;

    @Column(name = "maximo_intentos")
    private Integer maximoIntentos;

    @Column(name = "tiempo_limite_minutos")
    private Integer tiempoLimiteMinutos;

    @Column(name = "barajar_preguntas", nullable = false)
    private boolean barajarPreguntas = false;

    @Column(name = "barajar_opciones", nullable = false)
    private boolean barajarOpciones = false;

    @Column(name = "mostrar_respuestas", nullable = false, length = 20)
    private String mostrarRespuestas = "AL_APROBAR";

    @Column(name = "fecha_habilitacion")
    private Instant fechaHabilitacion;

    @Column(name = "bloquea_siguiente_modulo", nullable = false)
    private boolean bloqueaSiguienteModulo = false;

    @Column(name = "dias_revision", nullable = false)
    private Integer diasRevision = 3;

    @Column(nullable = false)
    private boolean activo = true;
}
