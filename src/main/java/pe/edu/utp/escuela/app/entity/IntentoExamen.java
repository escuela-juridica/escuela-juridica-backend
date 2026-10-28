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

/**
 * Intento de un alumno en un examen (HU-028 / HU-029). El backend es la única fuente de tiempo,
 * puntaje y estado: el navegador nunca informa el tiempo restante ni la nota.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "intento_examen")
public class IntentoExamen {

    public static final String EN_PROGRESO = "EN_PROGRESO";
    public static final String PENDIENTE_REVISION = "PENDIENTE_REVISION";
    public static final String CALIFICADO = "CALIFICADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "intento_examen_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examen_id", nullable = false)
    private Examen examen;

    @Column(name = "numero_intento", nullable = false)
    private Integer numeroIntento;

    /** EN_PROGRESO | PENDIENTE_REVISION | CALIFICADO | VENCIDO */
    @Column(nullable = false, length = 25)
    private String estado;

    /** Suma definitiva; vacío mientras exista una respuesta abierta sin revisar. */
    @Column(name = "puntaje_obtenido", precision = 8, scale = 2)
    private BigDecimal puntajeObtenido;

    /** Puntaje total congelado al iniciar el intento. */
    @Column(name = "puntaje_total", nullable = false, precision = 8, scale = 2)
    private BigDecimal puntajeTotal;

    @Column(precision = 5, scale = 2)
    private BigDecimal nota;

    @Column
    private Boolean aprobado;

    @Column(name = "iniciado_en", nullable = false)
    private Instant iniciadoEn;

    /** Límite calculado al iniciar cuando el examen tiene tiempo máximo. */
    @Column(name = "expira_en")
    private Instant expiraEn;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    @Column(name = "calificado_en")
    private Instant calificadoEn;

    public boolean estaEnProgreso() {
        return EN_PROGRESO.equals(estado);
    }

    public boolean estaPendienteRevision() {
        return PENDIENTE_REVISION.equals(estado);
    }
}
