package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.ReglaCurso;

public interface ReglaCursoRepositorio extends JpaRepository<ReglaCurso, Long> {
}
