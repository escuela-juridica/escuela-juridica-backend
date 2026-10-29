package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import pe.edu.utp.escuela.app.entity.ReglaCurso;

public interface ReglaCursoRepository extends JpaRepository<ReglaCurso, Long> {
    Optional<ReglaCurso> findByCurso_Id(Long cursoId);

    List<ReglaCurso> findByCurso_IdIn(Collection<Long> cursoIds);

    void deleteByCurso_Id(Long cursoId);
}
