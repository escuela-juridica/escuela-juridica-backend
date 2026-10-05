package pe.edu.utp.escuela.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.ConfiguracionInstitucional;

public interface ConfiguracionInstitucionalRepositorio extends JpaRepository<ConfiguracionInstitucional, Long> {
    List<ConfiguracionInstitucional> findAllByOrderByCodigoAsc();
    Optional<ConfiguracionInstitucional> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
}
