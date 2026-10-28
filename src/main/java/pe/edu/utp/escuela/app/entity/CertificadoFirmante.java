package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HU-032 — Copia congelada de un firmante tal como aparece impreso en un certificado. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "certificado_firmante")
@IdClass(CertificadoFirmanteId.class)
public class CertificadoFirmante {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificado_id", nullable = false)
    private Certificado certificado;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "firmante_id", nullable = false)
    private Firmante firmante;

    @Column(nullable = false)
    private Integer orden;

    @Column(name = "nombre_impreso", nullable = false, length = 200)
    private String nombreImpreso;

    @Column(name = "cargo_impreso", nullable = false, length = 180)
    private String cargoImpreso;

    @Column(name = "imagen_firma_url", columnDefinition = "text")
    private String imagenFirmaUrl;
}
