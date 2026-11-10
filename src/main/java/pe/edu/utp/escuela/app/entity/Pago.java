package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "pago")
public class Pago extends RegistroAuditable {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String ERROR = "ERROR";
    public static final String EXPIRADO = "EXPIRADO";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pago_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false) private Matricula matricula;
    @Column(name = "numero_pedido", nullable = false, unique = true, length = 60) private String numeroPedido;
    @Column(name = "operacion_proveedor", unique = true, length = 120) private String operacionProveedor;
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
    @Column(name = "ultimos_digitos", length = 4) private String ultimosDigitos;
    @Column(name = "numero_cuotas") private Integer numeroCuotas;
    @Column(name = "condiciones_validas_al_iniciar", nullable = false) private boolean condicionesValidasAlIniciar = true;
    @Column(columnDefinition = "text") private String motivo;
    @Column(name = "constancia_numero", unique = true, length = 80) private String constanciaNumero;
    @Column(name = "registrado_por_usuario_id") private Long registradoPorUsuarioId;
    @Column(name = "comprobante_url", length = 255) private String comprobanteUrl;
    @Column(name = "resultado_en") private Instant resultadoEn;
    @Column(name = "requiere_atencion", nullable = false) private boolean requiereAtencion = false;
    @Column(name = "detalle_atencion", columnDefinition = "text") private String detalleAtencion;
    @Column(name = "atendido_en") private Instant atendidoEn;
    @Column(name = "atendido_por_usuario_id") private Long atendidoPorUsuarioId;
    @Column(name = "iniciado_en", nullable = false) private Instant iniciadoEn = Instant.now();

    public boolean esAprobado() {
        return APROBADO.equals(estado);
    }

    public boolean esNoAprobadoFinal() {
        return RECHAZADO.equals(estado) || ERROR.equals(estado) || EXPIRADO.equals(estado);
    }
}
