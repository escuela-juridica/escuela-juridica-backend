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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "logro_certificacion")
public class LogroCertificacion {

    public static final String EN_PROGRESO = "EN_PROGRESO";
    public static final String ESPERA = "ESPERA";
    public static final String DATOS_PENDIENTES = "DATOS_PENDIENTES";
    public static final String LISTO = "LISTO";
    public static final String EMITIDO = "EMITIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "logro_certificacion_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false, unique = true)
    private Matricula matricula;

    @Column(nullable = false, length = 25)
    private String estado = EN_PROGRESO;

    @Column(name = "nota_final", precision = 5, scale = 2)
    private BigDecimal notaFinal;

    @Column(name = "progreso_final", precision = 5, scale = 2)
    private BigDecimal progresoFinal;

    @Column(name = "asistencia_final", precision = 5, scale = 2)
    private BigDecimal asistenciaFinal;

    @Column(length = 15)
    private String nivel;

    @Column(name = "fecha_finalizacion")
    private Instant fechaFinalizacion;

    @Column(name = "fecha_emision_programada")
    private Instant fechaEmisionProgramada;

    @Column(name = "datos_confirmados_en")
    private Instant datosConfirmadosEn;

    @Column(name = "nombres_confirmados", length = 240)
    private String nombresConfirmados;

    @Column(name = "modificado_en", nullable = false)
    private Instant modificadoEn;
}
