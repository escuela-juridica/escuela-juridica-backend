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

/**
 * Asistencia a una sesion en vivo (HU-026 la registra automaticamente al abrir el enlace dentro
 * de la ventana; HU-027 permite corregirla despues de realizada).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "asistencia")
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asistencia_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leccion_id", nullable = false)
    private Leccion leccion;

    /** PENDIENTE | PRESENTE | AUSENTE | JUSTIFICADA */
    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "registrada_por_usuario_id")
    private Long registradaPorUsuarioId;

    @Column(name = "motivo_correccion", columnDefinition = "text")
    private String motivoCorreccion;

    @Column(name = "registrada_en", nullable = false)
    private Instant registradaEn;

    @Column(name = "modificada_en", nullable = false)
    private Instant modificadaEn;
}
