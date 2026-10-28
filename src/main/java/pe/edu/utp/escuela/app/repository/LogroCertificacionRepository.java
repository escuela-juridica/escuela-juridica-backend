package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.LogroCertificacion;

public interface LogroCertificacionRepository extends JpaRepository<LogroCertificacion, Long> {

    Optional<LogroCertificacion> findByMatricula_Id(Long matriculaId);

    /** HU-032: logros todavía no emitidos, que el job revisa para emitir o pedir datos. */
    List<LogroCertificacion> findByEstadoIn(Collection<String> estados);
}
