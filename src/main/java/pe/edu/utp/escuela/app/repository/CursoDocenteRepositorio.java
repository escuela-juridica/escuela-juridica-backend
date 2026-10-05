package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.DocenteCursoFila;
import pe.edu.utp.escuela.app.dto.DocenteFichaFila;
import pe.edu.utp.escuela.app.entity.CursoDocente;
import pe.edu.utp.escuela.app.entity.CursoDocenteId;

public interface CursoDocenteRepositorio extends JpaRepository<CursoDocente, CursoDocenteId> {

    /** Para el editor administrativo: incluye docentes aunque ya no estén activos (igual que el
     * resto de HU-009, desactivar nunca borra ni oculta lo ya asignado). */
    @EntityGraph(attributePaths = "persona")
    List<CursoDocente> findByCurso_IdOrderByOrdenAsc(Long cursoId);

    void deleteAllByCurso_Id(Long cursoId);

    @Query("""
            select new pe.edu.utp.escuela.app.dto.DocenteCursoFila(
                cd.curso.id, p.id, p.nombres, p.apellidoPaterno,
                p.apellidoMaterno, p.fotoUrl, p.cargoProfesional, cd.orden)
            from CursoDocente cd
            join cd.persona p
            where cd.curso.id in :cursoIds and p.activo = true
            order by cd.curso.id, cd.orden
            """)
    List<DocenteCursoFila> buscarDocentesDeCursos(@Param("cursoIds") Collection<Long> cursoIds);

    @Query("""
            select new pe.edu.utp.escuela.app.dto.DocenteFichaFila(
                p.id, p.nombres, p.apellidoPaterno, p.apellidoMaterno,
                p.fotoUrl, p.cargoProfesional, p.biografiaProfesional, cd.orden)
            from CursoDocente cd
            join cd.persona p
            where cd.curso.id = :cursoId and p.activo = true
            order by cd.orden
            """)
    List<DocenteFichaFila> buscarDocentesDelCurso(@Param("cursoId") Long cursoId);
}
