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

/** HU-040 — Un intento de enviar la respuesta por correo: destino, resultado y responsable. Solo éxito o fallo. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "intento_envio_respuesta")
public class IntentoEnvioRespuesta {

    public static final String ENVIADO = "ENVIADO";
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "intento_envio_respuesta_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "respuesta_reclamacion_id", nullable = false)
    private RespuestaReclamacion respuesta;

    @Column(name = "correo_destino", nullable = false, length = 254)
    private String correoDestino;

    @Column(nullable = false, length = 10)
    private String resultado;

    @Column(name = "detalle_error", columnDefinition = "text")
    private String detalleError;

    @Column(name = "realizado_por_usuario_id", nullable = false)
    private Long realizadoPorUsuarioId;

    @Column(name = "realizado_en", nullable = false)
    private Instant realizadoEn;
}
