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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "certificado")
public class Certificado {

    public static final String VIGENTE = "VIGENTE";
    public static final String ANULADO = "ANULADO";

    public static Certificado actualEntre(Certificado a, Certificado b) {
        boolean aVigente = VIGENTE.equals(a.getEstado());
        boolean bVigente = VIGENTE.equals(b.getEstado());
        if (aVigente != bVigente) {
            return aVigente ? a : b;
        }
        return a.getEmitidoEn().isAfter(b.getEmitidoEn()) ? a : b;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificado_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "logro_certificacion_id", nullable = false)
    private LogroCertificacion logro;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(nullable = false, length = 15)
    private String estado = VIGENTE;

    @Column(name = "tipo_emision", nullable = false, length = 20)
    private String tipoEmision;

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

    @Column(name = "temario_impreso", columnDefinition = "text")
    private String temarioImpreso;

    @Column(name = "codigo_registro_impreso", length = 20)
    private String codigoRegistroImpreso;

    @Column(name = "lugar_emision", nullable = false, length = 180)
    private String lugarEmision;

    @Column(name = "entidad_impresa", length = 220)
    private String entidadImpresa;

    @Column(name = "nota_impresa", precision = 5, scale = 2)
    private BigDecimal notaImpresa;

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
