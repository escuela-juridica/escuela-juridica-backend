package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;
import pe.edu.utp.escuela.app.entity.Pago;

public interface PagoRepositorio extends JpaRepository<Pago, Long> {
    List<Pago> findByMatricula_IdOrderByResultadoEnDesc(Long matriculaId);
    List<Pago> findByMatricula_IdIn(Collection<Long> matriculaIds);
}
