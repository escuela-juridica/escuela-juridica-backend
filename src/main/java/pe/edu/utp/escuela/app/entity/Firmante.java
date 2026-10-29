package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "firmante")
public class Firmante extends RegistroAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "firmante_id")
    private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false, unique = true)
    private Persona persona;
    @Column(name = "cargo_firma", nullable = false, length = 180)
    private String cargoFirma;
    @Column(name = "imagen_firma_url", columnDefinition = "text")
    private String imagenFirmaUrl;
    @Column(nullable = false)
    private boolean activo = true;
}
