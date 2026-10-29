package pe.edu.utp.escuela.app.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import pe.edu.utp.escuela.app.entity.Rol;
public interface RolRepository extends JpaRepository<Rol, Long> {
    Optional<Rol> findByCodigoAndActivoTrue(String codigo);
}
