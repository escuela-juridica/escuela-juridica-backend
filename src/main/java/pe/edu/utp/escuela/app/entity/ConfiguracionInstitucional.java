package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "configuracion_institucional")
public class ConfiguracionInstitucional extends RegistroAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "configuracion_institucional_id")
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String codigo;
    @Column(nullable = false, columnDefinition = "text")
    private String valor;
    @Column(columnDefinition = "text")
    private String descripcion;
    @Column(name = "modificado_por_usuario_id")
    private Long modificadoPorUsuarioId;
}
