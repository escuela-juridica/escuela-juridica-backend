package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "entidad_certificadora")
public class EntidadCertificadora extends RegistroAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entidad_certificadora_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String nombre;

    @Column(name = "logo_url", columnDefinition = "text")
    private String logoUrl;

    @Column(nullable = false)
    private boolean activo = true;
}
