package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Recurso;

public interface RecursoRepositorio extends JpaRepository<Recurso, Long> {
}
