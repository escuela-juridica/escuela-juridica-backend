package pe.edu.utp.escuela.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Notificacion;

public interface NotificacionRepositorio extends JpaRepository<Notificacion, Long> {
    java.util.Optional<Notificacion> findTopByTipoOrderByCreadoEnDesc(String tipo);
}
