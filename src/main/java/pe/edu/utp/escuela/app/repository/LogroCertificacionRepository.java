package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.LogroCertificacion;

public interface LogroCertificacionRepository extends JpaRepository<LogroCertificacion, Long> {

    Optional<LogroCertificacion> findByMatricula_Id(Long matriculaId);

    List<LogroCertificacion> findByEstadoIn(Collection<String> estados);

    List<LogroCertificacion> findByMatricula_IdIn(Collection<Long> matriculaIds);

    @org.springframework.data.jpa.repository.Query("select count(l) from LogroCertificacion l where l.matricula.curso.id = :cursoId "
            + "and l.matricula.estado = 'ACTIVA' and l.fechaFinalizacion is not null")
    long contarFinalizadosActivos(@org.springframework.data.repository.query.Param("cursoId") Long cursoId);
}
