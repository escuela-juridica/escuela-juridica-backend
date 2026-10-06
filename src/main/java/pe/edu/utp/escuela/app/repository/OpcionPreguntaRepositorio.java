package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.OpcionPregunta;

public interface OpcionPreguntaRepositorio extends JpaRepository<OpcionPregunta, Long> {

    List<OpcionPregunta> findByPregunta_IdOrderByOrdenAsc(Long preguntaId);

    void deleteAllByPregunta_Id(Long preguntaId);
}
