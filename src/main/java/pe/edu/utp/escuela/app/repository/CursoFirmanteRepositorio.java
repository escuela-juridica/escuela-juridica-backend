package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.CursoFirmante;
import pe.edu.utp.escuela.app.entity.CursoFirmanteId;

public interface CursoFirmanteRepositorio extends JpaRepository<CursoFirmante, CursoFirmanteId> {
    @EntityGraph(attributePaths = "firmante.persona")
    List<CursoFirmante> findByCurso_IdOrderByOrdenAsc(Long cursoId);

    void deleteAllByCurso_Id(Long cursoId);
}
