package pe.edu.utp.escuela.app.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Certificado;

public interface CertificadoRepositorio extends JpaRepository<Certificado, Long> {
    Optional<Certificado> findByLogro_Matricula_Id(Long matriculaId);
}
