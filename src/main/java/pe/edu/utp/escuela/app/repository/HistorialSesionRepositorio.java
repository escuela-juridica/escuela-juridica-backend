package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.HistorialSesion;

public interface HistorialSesionRepositorio extends JpaRepository<HistorialSesion, Long> {

    List<HistorialSesion> findByLeccion_IdOrderByRealizadoEnDesc(Long leccionId);
}
