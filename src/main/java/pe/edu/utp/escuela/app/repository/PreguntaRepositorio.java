package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Pregunta;

public interface PreguntaRepositorio extends JpaRepository<Pregunta, Long> {

    List<Pregunta> findByExamen_IdOrderByOrdenAsc(Long examenId);

    long countByExamen_Id(Long examenId);
}
