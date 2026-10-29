package pe.edu.utp.escuela.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.IntentoEnvioRespuesta;

public interface IntentoEnvioRespuestaRepository extends JpaRepository<IntentoEnvioRespuesta, Long> {

    List<IntentoEnvioRespuesta> findByRespuesta_IdOrderByRealizadoEnDescIdDesc(Long respuestaId);

    Optional<IntentoEnvioRespuesta> findFirstByRespuesta_IdOrderByIdDesc(Long respuestaId);
}
