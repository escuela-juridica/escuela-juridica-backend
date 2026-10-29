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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HU-039 — Imagen opcional adjunta a una reclamación (por ejemplo, el comprobante de pago). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reclamacion_adjunto")
public class ReclamacionAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reclamacion_adjunto_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reclamacion_id", nullable = false)
    private Reclamacion reclamacion;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    /** Ubicación privada ({@code /uploads/reclamaciones/...}); solo administración la consulta. */
    @Column(nullable = false, columnDefinition = "text")
    private String referencia;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
