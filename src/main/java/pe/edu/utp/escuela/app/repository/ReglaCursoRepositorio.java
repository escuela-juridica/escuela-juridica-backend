package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import pe.edu.utp.escuela.app.entity.ReglaCurso;

public interface ReglaCursoRepositorio extends JpaRepository<ReglaCurso, Long> {
    Optional<ReglaCurso> findByCurso_Id(Long cursoId);

    void deleteByCurso_Id(Long cursoId);
}
