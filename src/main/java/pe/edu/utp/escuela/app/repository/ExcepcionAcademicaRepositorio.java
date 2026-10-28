package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.ExcepcionAcademica;

public interface ExcepcionAcademicaRepositorio extends JpaRepository<ExcepcionAcademica, Long> {

    List<ExcepcionAcademica> findByMatricula_IdOrderByRealizadoEnDesc(Long matriculaId);

    List<ExcepcionAcademica> findByMatricula_IdAndTipo(Long matriculaId, String tipo);

    List<ExcepcionAcademica> findByMatricula_IdInAndTipo(Collection<Long> matriculaIds, String tipo);
}
