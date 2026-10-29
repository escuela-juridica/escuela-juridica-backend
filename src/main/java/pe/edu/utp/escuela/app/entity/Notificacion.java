package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Registro simple de correos solicitados y su resultado de envío (ver notificacion en la BD). */
@Entity @Table(name = "notificacion") @Getter @Setter
public class Notificacion extends RegistroAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notificacion_id")
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false, length = 40)
    private String tipo;
    @Column(nullable = false, length = 254)
    private String destinatario;
    @Column(nullable = false, length = 220)
    private String asunto;
    @Column(name = "estado_envio", nullable = false, length = 15)
    private String estadoEnvio = "PENDIENTE";
    @Column(name = "intentos_envio", nullable = false)
    private int intentosEnvio;
    @Column(name = "ultimo_error", columnDefinition = "text")
    private String ultimoError;
    @Column(name = "enviado_en")
    private Instant enviadoEn;
}
