package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Pregunta;

public interface PreguntaRepositorio extends JpaRepository<Pregunta, Long> {

    List<Pregunta> findByExamen_IdOrderByOrdenAsc(Long examenId);

    List<Pregunta> findByExamen_IdIn(Collection<Long> examenIds);

    long countByExamen_Id(Long examenId);

    void deleteAllByExamen_IdIn(Collection<Long> examenIds);
}
