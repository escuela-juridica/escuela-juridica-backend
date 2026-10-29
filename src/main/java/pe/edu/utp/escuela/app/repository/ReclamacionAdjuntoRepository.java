package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.ReclamacionAdjunto;

public interface ReclamacionAdjuntoRepository extends JpaRepository<ReclamacionAdjunto, Long> {

    List<ReclamacionAdjunto> findByReclamacion_IdOrderByIdAsc(Long reclamacionId);

    Optional<ReclamacionAdjunto> findByIdAndReclamacion_Id(Long id, Long reclamacionId);

    @Query("select a.reclamacion.id, count(a) from ReclamacionAdjunto a where a.reclamacion.id in :ids group by a.reclamacion.id")
    List<Object[]> contarPorReclamacion(@Param("ids") Collection<Long> ids);

    /** Cantidad de imágenes de cada caso, sin una consulta por fila de la bandeja. */
    default Map<Long, Long> contarImagenes(Collection<Long> ids) {
        Map<Long, Long> conteo = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] fila : contarPorReclamacion(ids)) {
                conteo.put((Long) fila[0], (Long) fila[1]);
            }
        }
        return conteo;
    }
}
