package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.ConteoMatriculaFila;
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
