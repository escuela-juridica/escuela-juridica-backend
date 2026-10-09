package pe.edu.utp.escuela.app.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.TipoMaterial;

public interface TipoMaterialRepositorio extends JpaRepository<TipoMaterial, Long> {
    Page<TipoMaterial> findByActivoTrueOrderByNombreAsc(Pageable pageable);
    Page<TipoMaterial> findAllByOrderByNombreAsc(Pageable pageable);
    Optional<TipoMaterial> findByCodigo(String codigo);
}
