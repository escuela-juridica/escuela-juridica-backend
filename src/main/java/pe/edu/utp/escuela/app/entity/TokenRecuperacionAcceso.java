package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "token_recuperacion_acceso") @Getter @Setter
public class TokenRecuperacionAcceso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_recuperacion_id")
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "text")
    private String tokenHash;
    @Column(name = "estado_envio", nullable = false, length = 15)
    private String estadoEnvio = "PENDIENTE";
    @Column(name = "solicitado_en", nullable = false)
    private Instant solicitadoEn;
    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;
    @Column(name = "utilizado_en")
    private Instant utilizadoEn;
    @Column(name = "invalidado_en")
    private Instant invalidadoEn;
    @Column(name = "modificado_en", nullable = false)
    private Instant modificadoEn;
}
