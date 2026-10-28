package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.RevisionRespuesta;

public interface RevisionRespuestaRepository extends JpaRepository<RevisionRespuesta, Long> {

    /** Revisiones de varias respuestas, de la más reciente a la más antigua. */
    List<RevisionRespuesta> findByRespuestaIntento_IdInOrderByRevisadoEnDesc(Collection<Long> respuestaIds);
}
