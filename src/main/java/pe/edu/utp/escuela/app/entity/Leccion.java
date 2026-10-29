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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leccion")
public class Leccion extends RegistroAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leccion_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modulo_id", nullable = false)
    private Modulo modulo;

    @Column(name = "leccion_origen_id")
    private Long leccionOrigenId;

    @Column(nullable = false, length = 220)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "es_obligatoria", nullable = false)
    private boolean esObligatoria = true;

    @Column(name = "es_vista_previa", nullable = false)
    private boolean esVistaPrevia = false;

    @Column(name = "fecha_hora_inicio")
    private Instant fechaHoraInicio;

    @Column(name = "fecha_hora_fin")
    private Instant fechaHoraFin;

    @Column(name = "enlace_reunion", columnDefinition = "text")
    private String enlaceReunion;

    @Column(name = "motivo_cancelacion", columnDefinition = "text")
    private String motivoCancelacion;

    @Column(nullable = false)
    private boolean activo = true;
}
