package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Certificado;

public interface CertificadoRepositorio extends JpaRepository<Certificado, Long> {
    Optional<Certificado> findByLogro_Matricula_Id(Long matriculaId);

    /** HU-043: certificados de varias matrículas de una vez. */
    List<Certificado> findByLogro_Matricula_IdIn(Collection<Long> matriculaIds);
}
