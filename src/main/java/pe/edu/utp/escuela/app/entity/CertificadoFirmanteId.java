package pe.edu.utp.escuela.app.entity;

import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Clave compuesta de {@link CertificadoFirmante}: un firmante aparece una vez por certificado. */
@Getter
@Setter
@NoArgsConstructor
public class CertificadoFirmanteId implements Serializable {

    private Long certificado;
    private Long firmante;

    public CertificadoFirmanteId(Long certificado, Long firmante) {
        this.certificado = certificado;
        this.firmante = firmante;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CertificadoFirmanteId otro)) {
            return false;
        }
        return Objects.equals(certificado, otro.certificado) && Objects.equals(firmante, otro.firmante);
    }

    @Override
    public int hashCode() {
        return Objects.hash(certificado, firmante);
    }
}
