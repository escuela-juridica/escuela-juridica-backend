package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.BandejaRevisionRow;
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

    /**
     * HU-030: intentos con respuestas abiertas por revisar, uno por intento y con su fecha límite
     * más cercana. La bandeja los filtra por plazo y los ordena por esa fecha.
     */
    @Query("select new pe.edu.utp.escuela.app.dto.BandejaRevisionRow(i.id, m.id, "
            + "concat(p.nombres, ' ', p.apellidoPaterno), u.correo, c.id, c.titulo, e.id, e.titulo, i.numeroIntento, "
            + "i.enviadoEn, min(r.fechaLimiteRevision), count(r)) "
            + "from RespuestaIntento r join r.intentoExamen i join i.examen e join i.matricula m "
            + "join m.curso c join m.usuario u join u.persona p "
            + "where r.estadoRevision = 'PENDIENTE' and i.estado = 'PENDIENTE_REVISION' "
            + "and (:cursoId is null or c.id = :cursoId) "
            + "and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%') "
            + "or lower(e.titulo) like concat('%', :texto, '%') "
            + "or lower(u.correo) like concat('%', :texto, '%') "
            + "or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%')) "
            + "group by i.id, m.id, p.nombres, p.apellidoPaterno, u.correo, c.id, c.titulo, e.id, e.titulo, "
            + "i.numeroIntento, i.enviadoEn "
            + "order by min(r.fechaLimiteRevision) asc, i.id asc")
    List<BandejaRevisionRow> bandejaRevision(@Param("texto") String texto, @Param("cursoId") Long cursoId);
}
