package pe.edu.utp.escuela.app.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Reclamacion;

public interface ReclamacionRepository extends JpaRepository<Reclamacion, Long> {

    Optional<Reclamacion> findByCodigo(String codigo);
}
