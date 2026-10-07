package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.ConteoMatriculaFila;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import pe.edu.utp.escuela.app.entity.Matricula;

public interface MatriculaRepositorio extends JpaRepository<Matricula, Long> {

    boolean existsByCurso_Id(Long cursoId);

    boolean existsByUsuario_IdAndCurso_Id(Long usuarioId, Long cursoId);

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    Optional<Matricula> findByUsuario_IdAndCurso_Id(Long usuarioId, Long cursoId);

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    List<Matricula> findByUsuario_IdOrderByFechaMatriculaDesc(Long usuarioId);

    @EntityGraph(attributePaths = { "curso", "usuario", "usuario.persona" })
    List<Matricula> findAllByOrderByFechaMatriculaDesc();

    @Query(value = """
            select new pe.edu.utp.escuela.app.dto.MatriculaAdministrativaRespuesta(
                m.id, u.id, concat(p.nombres, ' ', p.apellidoPaterno), u.correo,
                c.id, c.titulo, m.estado, m.formaIngreso, m.fechaMatricula, m.fechaVencimiento)
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            where (:estado = '' or m.estado = :estado)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
            order by m.fechaMatricula desc
            """,
            countQuery = """
            select count(m)
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            where (:estado = '' or m.estado = :estado)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
            """)
    Page<MatriculaAdministrativaRespuesta> buscarAdministrativas(
            @Param("texto") String texto, @Param("estado") String estado, Pageable pageable);

    @Query(value = """
            select new pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta(
                m.id, concat(p.nombres, ' ', p.apellidoPaterno), u.correo, c.titulo, c.modalidad,
                m.fechaMatricula, m.fechaActivacion, m.estado, m.formaIngreso,
                case when m.fechaFinalizacion is null then 'EN_CURSO' else 'FINALIZADO' end)
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            where (:estado = '' or m.estado = :estado)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
            order by m.fechaMatricula desc
            """,
            countQuery = """
            select count(m)
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            where (:estado = '' or m.estado = :estado)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
            """)
    Page<ReporteMatriculaRespuesta> buscarReporte(
            @Param("texto") String texto, @Param("estado") String estado, Pageable pageable);

    @Query("""
            select new pe.edu.utp.escuela.app.dto.ConteoMatriculaFila(m.curso.id, count(m.id))
            from Matricula m
            where m.curso.id in :cursoIds and m.estado = 'ACTIVA'
            group by m.curso.id
            """)
    List<ConteoMatriculaFila> contarActivasPorCurso(@Param("cursoIds") Collection<Long> cursoIds);

    default Map<Long, Long> contarActivas(Collection<Long> cursoIds) {
        if (cursoIds.isEmpty()) {
            return Map.of();
        }
        return contarActivasPorCurso(cursoIds).stream()
                .collect(Collectors.toMap(ConteoMatriculaFila::cursoId, ConteoMatriculaFila::activas));
    }
}
