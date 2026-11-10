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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "revision_respuesta")
public class RevisionRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revision_respuesta_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "respuesta_intento_id", nullable = false)
    private RespuestaIntento respuestaIntento;

    @Column(name = "puntaje_anterior", precision = 6, scale = 2)
    private BigDecimal puntajeAnterior;

    @Column(name = "puntaje_asignado", nullable = false, precision = 6, scale = 2)
    private BigDecimal puntajeAsignado;

    @Column(columnDefinition = "text")
    private String observacion;

    @Column(name = "revisado_por_usuario_id", nullable = false)
    private Long revisadoPorUsuarioId;

    @Column(name = "revisado_en", nullable = false)
    private Instant revisadoEn;
}
