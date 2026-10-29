package pe.edu.utp.escuela.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.AtencionMatriculaPago;

public interface AtencionMatriculaPagoRepository extends JpaRepository<AtencionMatriculaPago, Long> {

    /**
     * HU-037 — Bandeja de excepciones: las pendientes primero y, dentro de cada grupo, las más recientes. El texto
     * busca por curso, correo o nombre del alumno.
     */
    @EntityGraph(attributePaths = { "matricula", "matricula.curso", "matricula.usuario", "matricula.usuario.persona",
            "pago" })
    @Query(value = """
            select a from AtencionMatriculaPago a
            join a.matricula m join m.usuario u join u.persona per join m.curso c
            where (:estado = '' or a.estado = :estado)
              and (:tipo = '' or a.tipo = :tipo)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(per.nombres, ' ', per.apellidoPaterno)) like concat('%', :texto, '%'))
            order by case when a.estado = 'PENDIENTE' then 0 else 1 end, a.creadoEn desc, a.id desc
            """,
            countQuery = """
            select count(a) from AtencionMatriculaPago a
            join a.matricula m join m.usuario u join u.persona per join m.curso c
            where (:estado = '' or a.estado = :estado)
              and (:tipo = '' or a.tipo = :tipo)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(per.nombres, ' ', per.apellidoPaterno)) like concat('%', :texto, '%'))
            """)
    Page<AtencionMatriculaPago> buscar(@Param("texto") String texto, @Param("estado") String estado,
            @Param("tipo") String tipo, Pageable pageable);

    @EntityGraph(attributePaths = { "matricula", "matricula.curso", "matricula.curso.estadoCurso", "matricula.usuario",
            "matricula.usuario.persona", "pago" })
    Optional<AtencionMatriculaPago> findWithDetalleById(Long id);

    /** El sobrecupo de un curso es una sola alerta atendible, aunque lo originen varias aprobaciones. */
    Optional<AtencionMatriculaPago> findFirstByTipoAndEstadoAndMatricula_Curso_IdOrderByIdAsc(String tipo, String estado,
            Long cursoId);

    boolean existsByPago_IdAndEstado(Long pagoId, String estado);

    List<AtencionMatriculaPago> findByPago_Id(Long pagoId);

    long countByEstado(String estado);
}
