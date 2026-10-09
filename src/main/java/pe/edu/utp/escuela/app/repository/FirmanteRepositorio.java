package pe.edu.utp.escuela.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Firmante;

public interface FirmanteRepositorio extends JpaRepository<Firmante, Long> {
    @EntityGraph(attributePaths = "persona")
    Page<Firmante> findByActivoTrueOrderByIdAsc(Pageable pageable);

    @EntityGraph(attributePaths = "persona")
    Page<Firmante> findAllByOrderByIdAsc(Pageable pageable);

    boolean existsByPersonaId(Long personaId);
}
