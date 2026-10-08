package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;

public interface ProgresoLeccionRepositorio extends JpaRepository<ProgresoLeccion, Long> {
    @EntityGraph(attributePaths = {"leccion", "leccion.modulo"})
    List<ProgresoLeccion> findByMatricula_Id(Long matriculaId);
}
