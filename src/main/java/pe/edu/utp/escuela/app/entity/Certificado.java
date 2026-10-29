package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HU-032 — Documento único emitido para un logro y verificable con su código. Al emitirse se
 * congelan nombres, curso, horas, fechas, nota y nivel: lo impreso no cambia aunque después
 * cambien el curso o el perfil.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "certificado")
public class Certificado {

    public static final String VIGENTE = "VIGENTE";
    public static final String ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificado_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "logro_certificacion_id", nullable = false, unique = true)
    private LogroCertificacion logro;

    @Column(nullable = false, length = 80)
    private String codigo;

    /** VIGENTE | ANULADO */
    @Column(nullable = false, length = 15)
    private String estado = VIGENTE;

    /** AUTOMATICA | SOLICITADA | MANUAL */
    @Column(name = "tipo_emision", nullable = false, length = 20)
    private String tipoEmision;

    /** NORMAL | REFRENDADO */
    @Column(nullable = false, length = 15)
    private String nivel;

    @Column(name = "nombres_impresos", nullable = false, length = 240)
    private String nombresImpresos;

    @Column(name = "titulo_curso_impreso", nullable = false, length = 220)
    private String tituloCursoImpreso;

    @Column(name = "tipo_curso_impreso", nullable = false, length = 120)
    private String tipoCursoImpreso;

    @Column(name = "horas_impresas", nullable = false, precision = 8, scale = 2)
    private BigDecimal horasImpresas;

    @Column(name = "fecha_inicio_impresa")
    private LocalDate fechaInicioImpresa;

    @Column(name = "fecha_fin_impresa")
    private LocalDate fechaFinImpresa;

    @Column(name = "lugar_emision", nullable = false, length = 180)
    private String lugarEmision;

    @Column(name = "entidad_impresa", length = 220)
    private String entidadImpresa;

    @Column(name = "nota_impresa", precision = 5, scale = 2)
    private BigDecimal notaImpresa;

    /** Referencia privada al PDF dentro del almacenamiento (nunca una URL pública). */
    @Column(name = "archivo_pdf_url", nullable = false, columnDefinition = "text")
    private String archivoPdfUrl;

    @Column(name = "motivo_emision_manual", columnDefinition = "text")
    private String motivoEmisionManual;

    @Column(name = "emitido_por_usuario_id")
    private Long emitidoPorUsuarioId;

    @Column(name = "emitido_en", nullable = false)
    private Instant emitidoEn;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "motivo_anulacion", columnDefinition = "text")
    private String motivoAnulacion;
}
