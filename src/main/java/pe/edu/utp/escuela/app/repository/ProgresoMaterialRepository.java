package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.ProgresoMaterial;

public interface ProgresoMaterialRepository extends JpaRepository<ProgresoMaterial, Long> {

    Optional<ProgresoMaterial> findByMatricula_IdAndMaterialLeccion_Id(Long matriculaId, Long materialLeccionId);

    List<ProgresoMaterial> findByMatricula_IdAndMaterialLeccion_IdIn(
            Long matriculaId, Collection<Long> materialLeccionIds);
}
