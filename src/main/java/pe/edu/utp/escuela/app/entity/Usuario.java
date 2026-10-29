package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "usuario") @Getter @Setter
public class Usuario extends RegistroAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false, unique = true)
    private Persona persona;
    @Column(nullable = false, length = 254, unique = true)
    private String correo;
    @Column(name = "origen_registro", nullable = false, length = 20)
    private String origenRegistro;
    @Column(name = "contrasena_hash", columnDefinition = "text")
    private String contrasenaHash;
    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;
    @Column(name = "correo_verificado_en")
    private Instant correoVerificadoEn;
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "requiere_cambio_contrasena", nullable = false)
    private boolean requiereCambioContrasena;
    @Column(name = "creado_por_usuario_id")
    private Long creadoPorUsuarioId;
    @Column(name = "deshabilitado_en")
    private Instant deshabilitadoEn;
}

