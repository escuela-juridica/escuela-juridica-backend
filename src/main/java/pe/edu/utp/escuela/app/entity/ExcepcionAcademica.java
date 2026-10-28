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

/** HU-031 — Excepción académica justificada: conserva quién, cuándo, por qué y el valor
 * anterior. Nunca sustituye un resultado: solo corrige asistencia o agrega un intento. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "excepcion_academica")
public class ExcepcionAcademica {

    public static final String REABRIR_INTENTO = "REABRIR_INTENTO";
    public static final String CORREGIR_ASISTENCIA = "CORREGIR_ASISTENCIA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "excepcion_academica_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @Column(nullable = false, length = 30)
    private String tipo;

    /** EXAMEN (intento adicional) o ASISTENCIA (corrección de una sesión). */
    @Column(name = "referencia_tipo", length = 30)
    private String referenciaTipo;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "valor_anterior", columnDefinition = "text")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "text")
    private String valorNuevo;

    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    @Column(name = "realizado_por_usuario_id", nullable = false)
    private Long realizadoPorUsuarioId;

    @Column(name = "realizado_en", nullable = false)
    private Instant realizadoEn;
}
