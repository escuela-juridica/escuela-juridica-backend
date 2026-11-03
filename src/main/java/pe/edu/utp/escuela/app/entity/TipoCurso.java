package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tipo_curso")
public class TipoCurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tipo_curso_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** Sigla que identifica al tipo en el código de registro del curso (SM, DP…). */
    @Column(name = "sigla_registro", nullable = false, unique = true, length = 4)
    private String siglaRegistro;

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false)
    private Integer orden;
}
