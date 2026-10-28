package pe.edu.utp.escuela.app.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.TokenRecuperacionAcceso;

public interface TokenRecuperacionAccesoRepository
        extends JpaRepository<TokenRecuperacionAcceso, Long> {

    @Modifying
    @Query("""
        update TokenRecuperacionAcceso t
           set t.invalidadoEn = :ahora, t.modificadoEn = :ahora
         where t.usuario.id = :usuarioId
           and t.utilizadoEn is null and t.invalidadoEn is null
        """)
    int invalidarPendientes(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);

    @Modifying
    @Query("""
        update TokenRecuperacionAcceso t
           set t.invalidadoEn = :ahora, t.modificadoEn = :ahora
         where t.usuario.id = :usuarioId and t.id <> :tokenUsadoId
           and t.utilizadoEn is null and t.invalidadoEn is null
        """)
    int invalidarOtrosPendientes(@Param("usuarioId") Long usuarioId,
            @Param("tokenUsadoId") Long tokenUsadoId, @Param("ahora") Instant ahora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t from TokenRecuperacionAcceso t
        join fetch t.usuario u
        where t.tokenHash = :hash
        """)
    Optional<TokenRecuperacionAcceso> bloquearPorHash(@Param("hash") String hash);

    /** Solo lectura, sin bloqueo: para la consulta de vigencia (GET) que no modifica nada. */
    Optional<TokenRecuperacionAcceso> findByTokenHash(String hash);
}
