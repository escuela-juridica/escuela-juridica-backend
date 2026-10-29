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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HU-024 — Seguimiento reproducido de cada video protegido por matrícula. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "progreso_material")
public class ProgresoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "progreso_material_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_leccion_id", nullable = false)
    private MaterialLeccion materialLeccion;

    @Column(name = "segundos_reproducidos", nullable = false)
    private Integer segundosReproducidos = 0;

    @Column(name = "porcentaje_video", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeVideo = BigDecimal.ZERO;

    @Column(name = "ultima_posicion_segundos", nullable = false)
    private Integer ultimaPosicionSegundos = 0;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
