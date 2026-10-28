package pe.edu.utp.escuela.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.Asistencia;

public interface AsistenciaRepositorio extends JpaRepository<Asistencia, Long> {

    Optional<Asistencia> findByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);

    List<Asistencia> findByMatricula_Id(Long matriculaId);

    List<Asistencia> findByLeccion_Id(Long leccionId);

    boolean existsByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);

    /** HU-027: asistencia de una sesión con el alumno ya resuelto, para el modal "Corregir". */
    @Query("""
            select a from Asistencia a
            join fetch a.matricula m
            join fetch m.usuario u
            join fetch u.persona p
            where a.leccion.id = :leccionId
            order by p.apellidoPaterno asc, p.nombres asc
            """)
    List<Asistencia> buscarDeLeccionConAlumno(@Param("leccionId") Long leccionId);
}
