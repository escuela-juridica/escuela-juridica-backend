package pe.edu.utp.escuela.app.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.NotaExamenFila;
import pe.edu.utp.escuela.app.entity.IntentoExamen;

public interface IntentoExamenRepository extends JpaRepository<IntentoExamen, Long> {

    /** HU-025: exámenes que la matrícula ya aprobó en al menos un intento. */
    @Query("select distinct i.examen.id from IntentoExamen i "
            + "where i.matricula.id = :matriculaId and i.aprobado = true")
    List<Long> idsExamenesAprobados(@Param("matriculaId") Long matriculaId);

    /** HU-025: mejor nota calificada por examen de la matrícula. */
    @Query("select new pe.edu.utp.escuela.app.dto.NotaExamenFila(i.examen.id, max(i.nota)) "
            + "from IntentoExamen i where i.matricula.id = :matriculaId "
            + "and i.estado = 'CALIFICADO' and i.nota is not null group by i.examen.id")
    List<NotaExamenFila> mejoresNotas(@Param("matriculaId") Long matriculaId);

    /** HU-028: todos los intentos de la matrícula (la ruta y el aula los agrupan por examen). */
    List<IntentoExamen> findByMatricula_IdOrderByIniciadoEnAsc(Long matriculaId);

    List<IntentoExamen> findByMatricula_IdInOrderByIniciadoEnAsc(Collection<Long> matriculaIds);

    List<IntentoExamen> findByMatricula_IdAndExamen_IdOrderByNumeroIntentoAsc(Long matriculaId, Long examenId);

    /** HU-028: intentos en curso cuyo tiempo ya venció (los cierra el job y cualquier lectura). */
    List<IntentoExamen> findByEstadoAndExpiraEnBefore(String estado, Instant ahora);

    /** Bloqueo de escritura: evita que un envío manual y el cierre por vencimiento califiquen dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IntentoExamen i where i.id = :id")
    Optional<IntentoExamen> buscarConBloqueo(@Param("id") Long id);
}
