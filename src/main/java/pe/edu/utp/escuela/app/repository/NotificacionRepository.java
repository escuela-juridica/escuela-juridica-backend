package pe.edu.utp.escuela.app.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    java.util.Optional<Notificacion> findTopByTipoOrderByCreadoEnDesc(String tipo);

    boolean existsByUsuario_IdAndTipo(Long usuarioId, String tipo);

    /** HU-038: avisos de un mismo evento (p. ej. la cancelación de un curso), los fallidos o pendientes primero. */
    @EntityGraph(attributePaths = { "usuario", "usuario.persona" })
    Page<Notificacion> findByTipoOrderByEstadoEnvioDescIdAsc(String tipo, Pageable pageable);

    @EntityGraph(attributePaths = { "usuario", "usuario.persona" })
    List<Notificacion> findByTipoAndEstadoEnvioNotOrderByIdAsc(String tipo, String estadoEnvio);

    long countByTipo(String tipo);

    long countByTipoAndEstadoEnvio(String tipo, String estadoEnvio);
}
