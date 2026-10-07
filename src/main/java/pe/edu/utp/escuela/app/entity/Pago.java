package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;

/** Registro economico separado de la matricula. HU-047 agregara intentos Culqi. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "pago")
public class Pago extends RegistroAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pago_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false) private Matricula matricula;
    @Column(name = "numero_pedido", nullable = false, unique = true, length = 60) private String numeroPedido;
    @Column(nullable = false, length = 20) private String origen;
    @Column(length = 30) private String medio;
    @Column(nullable = false, length = 20) private String estado;
    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)") private String moneda = "PEN";
    @Column(name = "precio_regular_aplicado", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioRegularAplicado;
    @Column(name = "precio_promocional_aplicado", precision = 10, scale = 2)
    private BigDecimal precioPromocionalAplicado;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal importe;
    @Column(name = "referencia_externa", length = 180) private String referenciaExterna;
    @Column(columnDefinition = "text") private String motivo;
    @Column(name = "registrado_por_usuario_id") private Long registradoPorUsuarioId;
    @Column(name = "resultado_en") private Instant resultadoEn;
}
