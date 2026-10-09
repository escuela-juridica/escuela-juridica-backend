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

/** Reglas académicas y de certificación configurables por curso (HU-014). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "regla_curso")
public class ReglaCurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regla_curso_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false, unique = true)
    private Curso curso;

    @Column(name = "requiere_examenes", nullable = false)
    private boolean requiereExamenes = true;

    @Column(name = "requiere_progreso", nullable = false)
    private boolean requiereProgreso = true;

    @Column(name = "requiere_asistencia", nullable = false)
    private boolean requiereAsistencia = false;

    @Column(name = "nota_minima", nullable = false, precision = 5, scale = 2)
    private BigDecimal notaMinima = BigDecimal.valueOf(12);

    @Column(name = "nota_refrendado", nullable = false, precision = 5, scale = 2)
    private BigDecimal notaRefrendado = BigDecimal.valueOf(14);

    @Column(name = "progreso_minimo", nullable = false, precision = 5, scale = 2)
    private BigDecimal progresoMinimo = BigDecimal.valueOf(80);

    @Column(name = "umbral_video", nullable = false, precision = 5, scale = 2)
    private BigDecimal umbralVideo = BigDecimal.valueOf(50);

    @Column(name = "asistencia_minima", nullable = false, precision = 5, scale = 2)
    private BigDecimal asistenciaMinima = BigDecimal.valueOf(80);

    @Column(name = "secuencia_obligatoria", nullable = false)
    private boolean secuenciaObligatoria = true;

    @Column(name = "dias_espera_certificado", nullable = false)
    private Integer diasEsperaCertificado = 0;

    @Column(name = "bloqueado_en")
    private Instant bloqueadoEn;
}
