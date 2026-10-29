package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.EventoPago;

public interface EventoPagoRepository extends JpaRepository<EventoPago, Long> {

    boolean existsByIdentificadorEvento(String identificadorEvento);

    java.util.Optional<EventoPago> findFirstByPago_IdOrderByIdDesc(Long pagoId);
}
