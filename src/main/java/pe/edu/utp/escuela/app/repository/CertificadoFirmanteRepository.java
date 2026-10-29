package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.CertificadoFirmante;
import pe.edu.utp.escuela.app.entity.CertificadoFirmanteId;

public interface CertificadoFirmanteRepository extends JpaRepository<CertificadoFirmante, CertificadoFirmanteId> {

    List<CertificadoFirmante> findByCertificado_IdOrderByOrdenAsc(Long certificadoId);
}
