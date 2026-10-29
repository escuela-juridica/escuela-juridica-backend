package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "regla_archivo")
public class ReglaArchivo extends RegistroAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regla_archivo_id")
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_material_id", nullable = false)
    private TipoMaterial tipoMaterial;
    @Column(nullable = false, length = 15)
    private String extension;
    @Column(name = "tamano_maximo_bytes", nullable = false)
    private long tamanoMaximoBytes;
    @Column(nullable = false)
    private boolean activo = true;
}
