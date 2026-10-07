package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Pago;

public interface PagoRepositorio extends JpaRepository<Pago, Long> {
}
