package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import pe.edu.utp.escuela.app.dto.DuracionLeccionFila;
import pe.edu.utp.escuela.app.dto.RecursoVistaPreviaFila;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;

public interface MaterialLeccionRepositorio extends JpaRepository<MaterialLeccion, Long> {

    /** Para el editor administrativo: incluye materiales desactivados (quitar nunca borra). */
    @EntityGraph(attributePaths = { "recurso", "recurso.tipoMaterial" })
    List<MaterialLeccion> findByLeccion_IdOrderByOrdenAsc(Long leccionId);

    @EntityGraph(attributePaths = { "recurso", "recurso.tipoMaterial" })
    List<MaterialLeccion> findByLeccion_IdInOrderByLeccion_IdAscOrdenAsc(Collection<Long> leccionIds);

    long countByLeccion_Id(Long leccionId);

    void deleteAllByLeccion_IdIn(Collection<Long> leccionIds);

    @Query("""
            select ml.leccion.id as leccionId, max(r.duracionSegundos) as duracionSegundos
            from MaterialLeccion ml join ml.recurso r
            where ml.leccion.id in :leccionIds
              and ml.activo = true and r.activo = true and r.tipo = 'VIDEO'
            group by ml.leccion.id
            """)
    List<DuracionLeccionFila> buscarDuraciones(@Param("leccionIds") Collection<Long> leccionIds);

    @Query("""
            select new pe.edu.utp.escuela.app.dto.RecursoVistaPreviaFila(
                l.id, ml.id, ml.titulo, ml.orden, r.tipo, r.origen,
                r.referencia, r.nombreArchivo, r.tipoMime, r.duracionSegundos,
                ml.permiteDescarga)
            from MaterialLeccion ml
            join ml.leccion l join l.modulo m join m.curso c join ml.recurso r
            where c.urlAmigable = :slug and l.id = :leccionId
              and c.publicadoEn is not null
              and m.activo = true and l.activo = true and l.esVistaPrevia = true
              and ml.activo = true and r.activo = true
            order by ml.orden
            """)
    List<RecursoVistaPreviaFila> buscarVistaPrevia(
            @Param("slug") String slug, @Param("leccionId") Long leccionId);
}
