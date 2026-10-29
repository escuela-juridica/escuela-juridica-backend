package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.HistorialCertificado;

public interface HistorialCertificadoRepository extends JpaRepository<HistorialCertificado, Long> {

    List<HistorialCertificado> findByCertificado_IdOrderByRealizadoEnDescIdDesc(Long certificadoId);
}
