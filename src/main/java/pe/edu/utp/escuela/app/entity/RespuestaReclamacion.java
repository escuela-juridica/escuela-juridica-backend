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

/**
 * HU-040 — Respuesta institucional a una queja o reclamo. Se conserva antes de intentar el envío y nunca se edita:
 * un envío fallido o un reenvío reutilizan este mismo texto y su fecha original. Solo existe una por caso.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "respuesta_reclamacion")
public class RespuestaReclamacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADO = "ENVIADO";
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "respuesta_reclamacion_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reclamacion_id", nullable = false)
    private Reclamacion reclamacion;

    @Column(nullable = false, columnDefinition = "text")
    private String respuesta;

    @Column(name = "respondido_por_usuario_id", nullable = false)
    private Long respondidoPorUsuarioId;

    /** PENDIENTE (aún no se envió), ENVIADO o ERROR (el último intento falló y el caso sigue pendiente). */
    @Column(name = "estado_envio", nullable = false, length = 15)
    private String estadoEnvio = PENDIENTE;

    /** Momento del primer envío aceptado. */
    @Column(name = "enviado_en")
    private Instant enviadoEn;

    /** Fecha original de la respuesta: no cambia aunque se reenvíe. */
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
