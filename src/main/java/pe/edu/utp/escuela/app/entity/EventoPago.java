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
 * HU-047 — Resultado comunicado por Culqi para un pago. El identificador es único: una confirmación repetida
 * se reconoce y no vuelve a producir efectos (pago, matrícula, acceso, cupo, constancia o correo).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "evento_pago")
public class EventoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evento_pago_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @Column(name = "identificador_evento", nullable = false, unique = true, length = 180)
    private String identificadorEvento;

    @Column(name = "estado_informado", nullable = false, length = 20)
    private String estadoInformado;

    /** Contenido mínimo para soporte; nunca datos sensibles de tarjeta. */
    @Column(columnDefinition = "text")
    private String contenido;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;

    @Column(name = "aplicado_en")
    private Instant aplicadoEn;
}
