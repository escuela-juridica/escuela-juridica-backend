package pe.edu.utp.escuela.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.CategoriaTematica;

public interface CategoriaTematicaRepositorio extends JpaRepository<CategoriaTematica, Long> {

    Page<CategoriaTematica> findByActivoTrueOrderByOrdenAscNombreAsc(Pageable pageable);
    Page<CategoriaTematica> findAllByOrderByOrdenAscNombreAsc(Pageable pageable);
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
