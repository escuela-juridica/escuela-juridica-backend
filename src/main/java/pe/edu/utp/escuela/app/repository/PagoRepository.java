package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import pe.edu.utp.escuela.app.entity.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByMatricula_IdOrderByResultadoEnDesc(Long matriculaId);
    List<Pago> findByMatricula_IdIn(Collection<Long> matriculaIds);

    /** HU-047: todos los intentos de una matrícula, del más reciente al más antiguo. */
    List<Pago> findByMatricula_IdOrderByIniciadoEnDescIdDesc(Long matriculaId);

    @EntityGraph(attributePaths = { "matricula", "matricula.curso", "matricula.usuario", "matricula.usuario.persona" })
    Optional<Pago> findByNumeroPedido(String numeroPedido);

    /** Bloquea el intento mientras se aplica un resultado, para que dos confirmaciones simultáneas no dupliquen efectos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.id = :id")
    Optional<Pago> bloquear(@Param("id") Long id);

    Optional<Pago> findByReferenciaExterna(String referenciaExterna);

    Optional<Pago> findByOperacionProveedor(String operacionProveedor);

    boolean existsByMatricula_IdAndEstado(Long matriculaId, String estado);

    Optional<Pago> findFirstByMatricula_IdAndEstado(Long matriculaId, String estado);
}
