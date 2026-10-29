package pe.edu.utp.escuela.app.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * HU-042 — Universo y filtros del reporte de pagos, compartidos por la página, el total y la exportación para que
     * nunca discrepen. El rango se aplica a la fecha del resultado (Culqi o registro administrativo); mientras un
     * intento sigue PENDIENTE se usa la fecha de inicio. Cada fila es un pago distinto: una confirmación repetida de
     * Culqi no crea otra fila (HU-047), así que nunca duplica el monto.
     */
    String ALCANCE_REPORTE = """
            from Pago p
            join p.matricula m join m.usuario u join u.persona per join m.curso c
            where (:cursoId is null or c.id = :cursoId)
              and (:estado = '' or p.estado = :estado)
              and (:medio = '' or lower(coalesce(p.medio, '')) = :medio)
              and (:origen = '' or p.origen = :origen)
              and (:texto = '' or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(per.nombres, ' ', per.apellidoPaterno)) like concat('%', :texto, '%')
                   or lower(p.numeroPedido) like concat('%', :texto, '%')
                   or lower(coalesce(p.referenciaExterna, '')) like concat('%', :texto, '%')
                   or lower(coalesce(p.operacionProveedor, '')) like concat('%', :texto, '%'))
              and coalesce(p.resultadoEn, p.iniciadoEn) >= :desde
              and coalesce(p.resultadoEn, p.iniciadoEn) < :hasta
            """;

    @EntityGraph(attributePaths = { "matricula", "matricula.curso", "matricula.usuario", "matricula.usuario.persona" })
    @Query(value = "select p " + ALCANCE_REPORTE + " order by coalesce(p.resultadoEn, p.iniciadoEn) desc, p.id desc",
            countQuery = "select count(p) " + ALCANCE_REPORTE)
    Page<Pago> buscarReporte(@Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("estado") String estado, @Param("medio") String medio, @Param("origen") String origen,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta, Pageable pageable);

    @EntityGraph(attributePaths = { "matricula", "matricula.curso", "matricula.usuario", "matricula.usuario.persona" })
    @Query("select p " + ALCANCE_REPORTE + " order by coalesce(p.resultadoEn, p.iniciadoEn) desc, p.id desc")
    List<Pago> exportarReporte(@Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("estado") String estado, @Param("medio") String medio, @Param("origen") String origen,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Suma de los pagos confirmados con monto mayor que cero dentro de los mismos filtros (nulo si no hay). */
    @Query("select sum(p.importe) " + ALCANCE_REPORTE
            + " and p.estado in ('APROBADO', 'REGISTRADO_MANUAL') and p.importe > 0")
    BigDecimal sumarConfirmados(@Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("estado") String estado, @Param("medio") String medio, @Param("origen") String origen,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("select count(p) " + ALCANCE_REPORTE
            + " and p.estado in ('APROBADO', 'REGISTRADO_MANUAL') and p.importe > 0")
    long contarConfirmados(@Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("estado") String estado, @Param("medio") String medio, @Param("origen") String origen,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** HU-037 — Pagos de un curso con una incidencia todavía sin atender (para el detalle del sobrecupo). */
    @EntityGraph(attributePaths = { "matricula", "matricula.usuario", "matricula.usuario.persona" })
    List<Pago> findByMatricula_Curso_IdAndRequiereAtencionTrueAndAtendidoEnIsNullOrderByIdAsc(Long cursoId);

    /** Medios distintos registrados (en minúsculas), para el filtro del reporte. */
    @Query("select distinct lower(p.medio) from Pago p where p.medio is not null order by lower(p.medio)")
    List<String> mediosRegistrados();
}
