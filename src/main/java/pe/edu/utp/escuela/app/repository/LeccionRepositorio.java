package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.Leccion;

public interface LeccionRepositorio extends JpaRepository<Leccion, Long> {

    @Query("""
            select l from Leccion l
            where l.modulo.id in :moduloIds and l.activo = true
            order by l.modulo.id, l.orden
            """)
    List<Leccion> buscarActivasDeModulos(@Param("moduloIds") Collection<Long> moduloIds);

    /** Para el editor administrativo: incluye lecciones desactivadas. */
    List<Leccion> findByModulo_IdOrderByOrdenAsc(Long moduloId);

    List<Leccion> findByModulo_IdInOrderByModulo_IdAscOrdenAsc(Collection<Long> moduloIds);

    long countByModulo_Id(Long moduloId);
}
