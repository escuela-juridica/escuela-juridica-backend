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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HU-039 — Queja o reclamo del Libro de Reclamaciones. Se conserva tal como la presentó la persona;
 * el vencimiento del plazo no cambia su contenido ni impide responderla.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reclamacion")
public class Reclamacion {

    public static final String QUEJA = "QUEJA";
    public static final String RECLAMO = "RECLAMO";
    public static final String PENDIENTE_RESPUESTA = "PENDIENTE_RESPUESTA";
    public static final String RESPONDIDO = "RESPONDIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reclamacion_id")
    private Long id;

    /** Número correlativo del caso: {@code LR-AAAA-NNNNNN}. */
    @Column(nullable = false, length = 40)
    private String codigo;

    /** Cuenta de quien reclama cuando presentó el formulario con sesión; vacío para un visitante. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "tipo_documento", nullable = false, length = 15)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(nullable = false, length = 160)
    private String apellidos;

    @Column(name = "correo_contacto", nullable = false, length = 254)
    private String correoContacto;

    @Column(name = "telefono_contacto", nullable = false, length = 30)
    private String telefonoContacto;

    @Column(nullable = false, length = 250)
    private String direccion;

    @Column(name = "monto_reclamado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoReclamado;

    @Column(name = "descripcion_bien", nullable = false, length = 300)
    private String descripcionBien;

    @Column(nullable = false, columnDefinition = "text")
    private String detalle;

    @Column(nullable = false, columnDefinition = "text")
    private String pedido;

    @Column(name = "declaracion_jurada", nullable = false)
    private boolean declaracionJurada;

    @Column(name = "autoriza_respuesta_correo", nullable = false)
    private boolean autorizaRespuestaCorreo;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE_RESPUESTA;

    @Column(name = "fecha_limite_respuesta", nullable = false)
    private LocalDate fechaLimiteRespuesta;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    @Column(name = "respondido_en")
    private Instant respondidoEn;

    /** PENDIENTE, ENVIADO o ERROR: solo el resultado del envío de la constancia, sin seguimiento de entrega. */
    @Column(name = "estado_envio_constancia", nullable = false, length = 15)
    private String estadoEnvioConstancia = "PENDIENTE";

    @Column(name = "ultimo_error_constancia", columnDefinition = "text")
    private String ultimoErrorConstancia;
}
