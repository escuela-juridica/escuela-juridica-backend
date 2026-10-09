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
import jakarta.persistence.OneToOne;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "matricula")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "matricula_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "forma_ingreso", nullable = false, length = 25)
    private String formaIngreso;

    @Column(name = "fecha_matricula", nullable = false)
    private Instant fechaMatricula;

    @Column(name = "fecha_activacion")
    private Instant fechaActivacion;

    @Column(name = "fecha_vencimiento")
    private Instant fechaVencimiento;

    @Column(name = "fecha_finalizacion")
    private Instant fechaFinalizacion;

    @Column(name = "motivo_cancelacion", columnDefinition = "text")
    private String motivoCancelacion;

    @Column(name = "cancelada_en")
    private Instant canceladaEn;

    @Column(name = "cancelada_por_usuario_id")
    private Long canceladaPorUsuarioId;

    @Column(name = "creado_por_usuario_id")
    private Long creadoPorUsuarioId;

    @OneToOne(mappedBy = "matricula", fetch = FetchType.LAZY)
    private LogroCertificacion logroCertificacion;
}
