package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "usuario_rol") @Getter @Setter
public class UsuarioRol {
    @EmbeddedId
    private Clave id;
    @Column(name = "es_principal", nullable = false)
    private boolean principal = true;
    @Column(name = "asignado_por_usuario_id")
    private Long asignadoPorUsuarioId;
    @Column(name = "asignado_en", nullable = false)
    private Instant asignadoEn;

    @Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        @Column(name = "usuario_id") private Long usuarioId;
        @Column(name = "rol_id") private Long rolId;
    }
}

