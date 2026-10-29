package pe.edu.utp.escuela.app.repository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.CodigoVerificacionCorreo;
public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacionCorreo, Long> {
    @Modifying
    @Query("update CodigoVerificacionCorreo c set c.invalidadoEn = :ahora, c.modificadoEn = :ahora where c.usuario.id = :id and c.utilizadoEn is null and c.invalidadoEn is null")
    void invalidarAnteriores(@Param("id") Long usuarioId, @Param("ahora") Instant ahora);

    List<CodigoVerificacionCorreo> findByUsuario_IdAndUtilizadoEnIsNullAndInvalidadoEnIsNull(Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodigoVerificacionCorreo c join fetch c.usuario where c.id = :id")
    Optional<CodigoVerificacionCorreo> findByIdParaActualizar(@Param("id") Long id);
}
