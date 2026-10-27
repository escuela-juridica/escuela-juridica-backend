package pe.edu.utp.escuela.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Asistencia;

public interface AsistenciaRepositorio extends JpaRepository<Asistencia, Long> {

    Optional<Asistencia> findByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);

    List<Asistencia> findByMatricula_Id(Long matriculaId);

    List<Asistencia> findByLeccion_Id(Long leccionId);

    boolean existsByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);
}
