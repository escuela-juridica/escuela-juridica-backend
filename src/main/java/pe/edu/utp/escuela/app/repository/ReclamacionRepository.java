package pe.edu.utp.escuela.app.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.Reclamacion;

public interface ReclamacionRepository extends JpaRepository<Reclamacion, Long> {

    Optional<Reclamacion> findByCodigo(String codigo);

    /**
     * HU-040 — Bandeja: los pendientes primero, del vencimiento más próximo al más lejano; después los respondidos,
     * del más reciente al más antiguo. El texto busca por número de caso, documento, nombre o correo.
     */
    @Query(value = """
            select r from Reclamacion r
            where (:estado = '' or r.estado = :estado)
              and (:tipo = '' or r.tipo = :tipo)
              and (:texto = '' or lower(r.codigo) like concat('%', :texto, '%')
                   or lower(r.numeroDocumento) like concat('%', :texto, '%')
                   or lower(concat(r.nombres, ' ', r.apellidos)) like concat('%', :texto, '%')
                   or lower(r.correoContacto) like concat('%', :texto, '%'))
            order by case when r.estado = 'PENDIENTE_RESPUESTA' then 0 else 1 end,
                     case when r.estado = 'PENDIENTE_RESPUESTA' then r.fechaLimiteRespuesta end asc,
                     r.respondidoEn desc, r.id desc
            """,
            countQuery = """
            select count(r) from Reclamacion r
            where (:estado = '' or r.estado = :estado)
              and (:tipo = '' or r.tipo = :tipo)
              and (:texto = '' or lower(r.codigo) like concat('%', :texto, '%')
                   or lower(r.numeroDocumento) like concat('%', :texto, '%')
                   or lower(concat(r.nombres, ' ', r.apellidos)) like concat('%', :texto, '%')
                   or lower(r.correoContacto) like concat('%', :texto, '%'))
            """)
    Page<Reclamacion> buscar(@Param("texto") String texto, @Param("estado") String estado,
            @Param("tipo") String tipo, Pageable pageable);

    long countByEstado(String estado);
}
