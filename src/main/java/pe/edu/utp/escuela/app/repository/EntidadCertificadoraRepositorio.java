package pe.edu.utp.escuela.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.EntidadCertificadora;

public interface EntidadCertificadoraRepositorio extends JpaRepository<EntidadCertificadora, Long> {
    Page<EntidadCertificadora> findByActivoTrueOrderByNombreAsc(Pageable pageable);
    Page<EntidadCertificadora> findAllByOrderByNombreAsc(Pageable pageable);
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
