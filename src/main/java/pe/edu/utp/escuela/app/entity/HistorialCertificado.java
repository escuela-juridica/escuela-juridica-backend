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

/** HU-035/HU-036 — Evidencia administrativa de un certificado: quién, cuándo, por qué y qué cambió. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "historial_certificado")
public class HistorialCertificado {

    public static final String EMISION_MANUAL = "EMISION_MANUAL";
    public static final String CORRECCION = "CORRECCION";
    public static final String ANULACION = "ANULACION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historial_certificado_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificado_id", nullable = false)
    private Certificado certificado;

    @Column(nullable = false, length = 15)
    private String accion;

    @Column(name = "datos_anteriores", columnDefinition = "text")
    private String datosAnteriores;

    @Column(name = "datos_nuevos", columnDefinition = "text")
    private String datosNuevos;

    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    @Column(name = "realizado_por_usuario_id", nullable = false)
    private Long realizadoPorUsuarioId;

    @Column(name = "realizado_en", nullable = false)
    private Instant realizadoEn;
}
