package pe.edu.utp.escuela.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.TipoCurso;

public interface TipoCursoRepositorio extends JpaRepository<TipoCurso, Long> {

    Page<TipoCurso> findByActivoTrueOrderByOrdenAscNombreAsc(Pageable pageable);
    Page<TipoCurso> findAllByOrderByOrdenAscNombreAsc(Pageable pageable);
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
