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
@Table(name = "atencion_matricula_pago")
public class AtencionMatriculaPago {

    public static final String PAGO_TARDIO = "PAGO_TARDIO";
    public static final String CURSO_CANCELADO = "CURSO_CANCELADO";
    public static final String MATRICULA_CANCELADA = "MATRICULA_CANCELADA";
    public static final String SOBRECUPO = "SOBRECUPO";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String ATENDIDO = "ATENDIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "atencion_matricula_pago_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id")
    private Pago pago;

    @Column(nullable = false, length = 35)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(columnDefinition = "text")
    private String resultado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "atendido_en")
    private Instant atendidoEn;

    @Column(name = "atendido_por_usuario_id")
    private Long atendidoPorUsuarioId;
}
