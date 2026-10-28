package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.NotaExamenFila;
import pe.edu.utp.escuela.app.entity.IntentoExamen;

public interface IntentoExamenRepositorio extends JpaRepository<IntentoExamen, Long> {

    /** HU-025: exámenes que la matrícula ya aprobó en al menos un intento. */
    @Query("select distinct i.examen.id from IntentoExamen i "
            + "where i.matricula.id = :matriculaId and i.aprobado = true")
    List<Long> idsExamenesAprobados(@Param("matriculaId") Long matriculaId);

    /** HU-025: mejor nota calificada por examen de la matrícula. */
    @Query("select new pe.edu.utp.escuela.app.dto.NotaExamenFila(i.examen.id, max(i.nota)) "
            + "from IntentoExamen i where i.matricula.id = :matriculaId "
            + "and i.estado = 'CALIFICADO' and i.nota is not null group by i.examen.id")
    List<NotaExamenFila> mejoresNotas(@Param("matriculaId") Long matriculaId);
}
