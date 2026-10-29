package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.ReclamacionAdjunto;

public interface ReclamacionAdjuntoRepository extends JpaRepository<ReclamacionAdjunto, Long> {

    List<ReclamacionAdjunto> findByReclamacion_IdOrderByIdAsc(Long reclamacionId);
}
