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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Intento de un alumno en un examen. Mapeo mínimo y de solo lectura para HU-025 (saber si un
 * examen bloqueante ya fue aprobado y con qué nota); rendir, calificar y registrar intentos es
 * Épica 5 (HU-028), que ampliará esta entidad con el resto de las columnas de la tabla.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "intento_examen")
public class IntentoExamen {

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

    @Column(precision = 5, scale = 2)
    private BigDecimal nota;

    @Column
    private Boolean aprobado;
}
