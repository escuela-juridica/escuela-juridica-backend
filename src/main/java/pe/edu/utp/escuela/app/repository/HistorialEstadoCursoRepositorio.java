package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.HistorialEstadoCurso;

public interface HistorialEstadoCursoRepositorio extends JpaRepository<HistorialEstadoCurso, Long> {
}
