package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Modulo;

public interface ModuloRepositorio extends JpaRepository<Modulo, Long> {

    List<Modulo> findByCursoIdAndActivoTrueOrderByOrdenAsc(Long cursoId);

    /** Para el editor administrativo: incluye módulos desactivados (desactivar nunca borra). */
    List<Modulo> findByCurso_IdOrderByOrdenAsc(Long cursoId);

    long countByCurso_Id(Long cursoId);
}
