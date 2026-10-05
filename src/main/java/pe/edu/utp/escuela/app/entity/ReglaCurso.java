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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Solo mapea lo necesario para crear la fila por defecto al dar de alta un curso (HU-010); el
 * resto de columnas (requiere_examenes, nota_minima, etc.) usa los valores por defecto de la base
 * de datos hasta que HU-014 los exponga y los edite. */
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
}
