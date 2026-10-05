package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "tipo_material")
public class TipoMaterial extends RegistroAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tipo_material_id")
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(columnDefinition = "text")
    private String descripcion;
    @Column(nullable = false)
    private boolean activo = true;
}
