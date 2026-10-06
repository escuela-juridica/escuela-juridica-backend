package pe.edu.utp.escuela.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.ReglaArchivo;

public interface ReglaArchivoRepositorio extends JpaRepository<ReglaArchivo, Long> {
    @EntityGraph(attributePaths = "tipoMaterial")
    List<ReglaArchivo> findByActivoTrueOrderByIdAsc();

    @EntityGraph(attributePaths = "tipoMaterial")
    List<ReglaArchivo> findAllByOrderByIdAsc();

    boolean existsByTipoMaterial_IdAndExtensionIgnoreCase(Long tipoMaterialId, String extension);
    boolean existsByTipoMaterial_IdAndExtensionIgnoreCaseAndIdNot(Long tipoMaterialId, String extension, Long id);

    /** Para validar un archivo subido (HU-011): el límite aplicable por tipo de material y
     * extensión, solo si la regla sigue activa. */
    Optional<ReglaArchivo> findByTipoMaterial_IdAndExtensionIgnoreCaseAndActivoTrue(Long tipoMaterialId, String extension);
}
