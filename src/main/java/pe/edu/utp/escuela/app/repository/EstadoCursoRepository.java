package pe.edu.utp.escuela.app.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.EstadoCurso;

public interface EstadoCursoRepository extends JpaRepository<EstadoCurso, Long> {
    Optional<EstadoCurso> findByCodigo(String codigo);
}
