package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.LogroCertificacion;

public interface LogroCertificacionRepository extends JpaRepository<LogroCertificacion, Long> {

    Optional<LogroCertificacion> findByMatricula_Id(Long matriculaId);

    /** HU-032: logros todavía no emitidos, que el job revisa para emitir o pedir datos. */
    List<LogroCertificacion> findByEstadoIn(Collection<String> estados);

    /** HU-038: logros de un conjunto de matrículas, para saber quién ya había finalizado. */
    List<LogroCertificacion> findByMatricula_IdIn(Collection<Long> matriculaIds);

    /** HU-038: alumnos con acceso que ya cumplieron las condiciones del curso (fecha de finalización registrada). */
    @org.springframework.data.jpa.repository.Query("select count(l) from LogroCertificacion l where l.matricula.curso.id = :cursoId "
            + "and l.matricula.estado = 'ACTIVA' and l.fechaFinalizacion is not null")
    long contarFinalizadosActivos(@org.springframework.data.repository.query.Param("cursoId") Long cursoId);
}
