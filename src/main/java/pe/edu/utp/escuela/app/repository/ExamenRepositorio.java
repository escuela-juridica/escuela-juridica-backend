package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Examen;

public interface ExamenRepositorio extends JpaRepository<Examen, Long> {

    @EntityGraph(attributePaths = "modulo")
    List<Examen> findByCurso_IdOrderByOrdenAsc(Long cursoId);

    List<Examen> findByModulo_IdOrderByOrdenAsc(Long moduloId);

    long countByCurso_Id(Long cursoId);

    boolean existsByCurso_IdAndTituloIgnoreCase(Long cursoId, String titulo);

    boolean existsByCurso_IdAndTituloIgnoreCaseAndIdNot(Long cursoId, String titulo, Long id);

    boolean existsByCurso_IdAndActivoTrueAndTipo(Long cursoId, String tipo);

    void deleteAllByModulo_Id(Long moduloId);
}
