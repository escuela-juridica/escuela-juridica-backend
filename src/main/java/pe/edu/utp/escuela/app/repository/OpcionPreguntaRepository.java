package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.OpcionPregunta;

public interface OpcionPreguntaRepository extends JpaRepository<OpcionPregunta, Long> {

    List<OpcionPregunta> findByPregunta_IdOrderByOrdenAsc(Long preguntaId);

    /** HU-028: alternativas de varias preguntas de una sola vez (calificar y armar el intento). */
    List<OpcionPregunta> findByPregunta_IdInOrderByPregunta_IdAscOrdenAsc(Collection<Long> preguntaIds);

    void deleteAllByPregunta_Id(Long preguntaId);

    void deleteAllByPregunta_IdIn(Collection<Long> preguntaIds);
}
