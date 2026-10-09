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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "recurso")
public class Recurso extends RegistroAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recurso_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_material_id", nullable = false)
    private TipoMaterial tipoMaterial;

    @Column(nullable = false, length = 15)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String origen;

    @Column(nullable = false, columnDefinition = "text")
    private String referencia;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "tipo_mime", length = 150)
    private String tipoMime;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "duracion_segundos")
    private Integer duracionSegundos;

    @Column(name = "duracion_detectada", nullable = false)
    private boolean duracionDetectada = false;

    @Column(name = "youtube_no_listado_confirmado")
    private Boolean youtubeNoListadoConfirmado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario creadoPorUsuario;

    @Column(nullable = false)
    private boolean activo = true;
}
