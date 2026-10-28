package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.PendienteRevisionRow;
import pe.edu.utp.escuela.app.entity.RespuestaIntento;

public interface RespuestaIntentoRepository extends JpaRepository<RespuestaIntento, Long> {

    @EntityGraph(attributePaths = {"opciones", "pregunta"})
    List<RespuestaIntento> findByIntentoExamen_Id(Long intentoId);

    @EntityGraph(attributePaths = {"opciones", "pregunta"})
    List<RespuestaIntento> findByIntentoExamen_IdIn(Collection<Long> intentoIds);

    @EntityGraph(attributePaths = "opciones")
    Optional<RespuestaIntento> findByIntentoExamen_IdAndPregunta_Id(Long intentoId, Long preguntaId);

    /** HU-043: respuestas abiertas por revisar de cada matrícula y la fecha límite más cercana. */
    @Query("select new pe.edu.utp.escuela.app.dto.PendienteRevisionRow(r.intentoExamen.matricula.id, count(r), "
            + "min(r.fechaLimiteRevision)) from RespuestaIntento r "
            + "where r.estadoRevision = 'PENDIENTE' and r.intentoExamen.matricula.id in :matriculaIds "
            + "group by r.intentoExamen.matricula.id")
    List<PendienteRevisionRow> pendientesPorMatricula(@Param("matriculaIds") Collection<Long> matriculaIds);
}
