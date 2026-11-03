package pe.edu.utp.escuela.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.TipoCurso;

public interface TipoCursoRepository extends JpaRepository<TipoCurso, Long> {

    Page<TipoCurso> findByActivoTrueOrderByOrdenAscNombreAsc(Pageable pageable);
    Page<TipoCurso> findAllByOrderByOrdenAscNombreAsc(Pageable pageable);
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    boolean existsBySiglaRegistroIgnoreCase(String sigla);
    boolean existsBySiglaRegistroIgnoreCaseAndIdNot(String sigla, Long id);

    /** Bloquea la fila del tipo mientras dura la transacción: serializa la numeración de sus cursos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TipoCurso t where t.id = :id")
    Optional<TipoCurso> bloquearPorId(@Param("id") Long id);
}
