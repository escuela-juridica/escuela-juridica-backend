package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.RespuestaReclamacion;

public interface RespuestaReclamacionRepository extends JpaRepository<RespuestaReclamacion, Long> {

    Optional<RespuestaReclamacion> findByReclamacion_Id(Long reclamacionId);

    @EntityGraph(attributePaths = "reclamacion")
    List<RespuestaReclamacion> findByReclamacion_IdIn(Collection<Long> reclamacionIds);
}
