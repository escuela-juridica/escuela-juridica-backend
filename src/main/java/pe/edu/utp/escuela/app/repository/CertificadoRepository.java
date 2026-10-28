package pe.edu.utp.escuela.app.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.Certificado;

public interface CertificadoRepository extends JpaRepository<Certificado, Long> {
    Optional<Certificado> findByLogro_Matricula_Id(Long matriculaId);

    /** HU-043: certificados de varias matrículas de una vez. */
    List<Certificado> findByLogro_Matricula_IdIn(Collection<Long> matriculaIds);

    Optional<Certificado> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    /** HU-033: certificados del alumno, del más reciente al más antiguo. */
    @EntityGraph(attributePaths = {"logro", "logro.matricula", "logro.matricula.curso"})
    List<Certificado> findByLogro_Matricula_Usuario_IdOrderByEmitidoEnDesc(Long usuarioId);

    /**
     * HU-044 — Universo y filtros del reporte de certificados, compartidos por la página y la
     * exportación para que pantalla y Excel nunca discrepen. El rango se aplica a {@code emitido_en}.
     * Los datos mostrados son los del certificado ya congelado.
     */
    String ALCANCE_REPORTE = """
            from Certificado c
            join c.logro l join l.matricula m join m.curso cu
            where (:cursoId is null or cu.id = :cursoId)
              and (:nivel = '' or c.nivel = :nivel)
              and (:estado = '' or c.estado = :estado)
              and (:forma = '' or c.tipoEmision = :forma)
              and (:entidad = '' or (:entidad = 'ninguna' and c.entidadImpresa is null)
                   or lower(coalesce(c.entidadImpresa, '')) = :entidad)
              and (:texto = '' or lower(c.nombresImpresos) like concat('%', :texto, '%')
                   or lower(c.codigo) like concat('%', :texto, '%')
                   or lower(c.tituloCursoImpreso) like concat('%', :texto, '%'))
              and c.emitidoEn >= :desde and c.emitidoEn < :hasta
            """;

    @EntityGraph(attributePaths = {"logro", "logro.matricula", "logro.matricula.curso"})
    @Query(value = "select c " + ALCANCE_REPORTE + " order by c.emitidoEn desc, c.id desc",
            countQuery = "select count(c) " + ALCANCE_REPORTE)
    Page<Certificado> buscarReporte(@Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("nivel") String nivel, @Param("estado") String estado, @Param("forma") String forma,
            @Param("entidad") String entidad, @Param("desde") Instant desde, @Param("hasta") Instant hasta,
            Pageable pageable);

    @EntityGraph(attributePaths = {"logro", "logro.matricula", "logro.matricula.curso"})
    @Query("select c " + ALCANCE_REPORTE + " order by c.emitidoEn desc, c.id desc")
    List<Certificado> exportarReporte(@Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("nivel") String nivel, @Param("estado") String estado, @Param("forma") String forma,
            @Param("entidad") String entidad, @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
