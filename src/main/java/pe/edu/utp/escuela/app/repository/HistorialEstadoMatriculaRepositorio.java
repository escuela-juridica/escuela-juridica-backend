package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.HistorialEstadoMatricula;

public interface HistorialEstadoMatriculaRepositorio extends JpaRepository<HistorialEstadoMatricula, Long> {
    List<HistorialEstadoMatricula> findByMatricula_IdOrderByRealizadoEnDesc(Long matriculaId);
}
