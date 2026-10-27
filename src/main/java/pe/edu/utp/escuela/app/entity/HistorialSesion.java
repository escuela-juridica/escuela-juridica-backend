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
 * HU-027 — Conserva cada reprogramación o cancelación de una sesión en vivo: el horario/enlace
 * anterior, el nuevo (vacío al cancelar) y el motivo obligatorio informado por administración.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "historial_sesion")
public class HistorialSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historial_sesion_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leccion_id", nullable = false)
    private Leccion leccion;

    /** REPROGRAMACION | CANCELACION */
    @Column(nullable = false, length = 20)
    private String accion;

    @Column(name = "inicio_anterior")
    private Instant inicioAnterior;

    @Column(name = "fin_anterior")
    private Instant finAnterior;

    @Column(name = "enlace_anterior", columnDefinition = "text")
    private String enlaceAnterior;

    @Column(name = "inicio_nuevo")
    private Instant inicioNuevo;

    @Column(name = "fin_nuevo")
    private Instant finNuevo;

    @Column(name = "enlace_nuevo", columnDefinition = "text")
    private String enlaceNuevo;

    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    @Column(name = "realizado_por_usuario_id", nullable = false)
    private Long realizadoPorUsuarioId;

    @Column(name = "realizado_en", nullable = false)
    private Instant realizadoEn;
}
