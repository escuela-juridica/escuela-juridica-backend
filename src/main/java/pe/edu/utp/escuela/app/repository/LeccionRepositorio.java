package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.Leccion;

public interface LeccionRepositorio extends JpaRepository<Leccion, Long> {

    @Query("""
            select l from Leccion l
            where l.modulo.id in :moduloIds and l.activo = true
            order by l.modulo.id, l.orden
            """)
    List<Leccion> buscarActivasDeModulos(@Param("moduloIds") Collection<Long> moduloIds);

    /** Para el editor administrativo: incluye lecciones desactivadas. */
    List<Leccion> findByModulo_IdOrderByOrdenAsc(Long moduloId);

    List<Leccion> findByModulo_IdInOrderByModulo_IdAscOrdenAsc(Collection<Long> moduloIds);

    long countByModulo_Id(Long moduloId);

    @Query("select count(l) from Leccion l where l.modulo.curso.id = :cursoId and l.tipo = 'EN_VIVO' "
            + "and l.activo = true and l.estado <> 'CANCELADA' and l.fechaHoraInicio > :desde")
    long contarSesionesFuturasActivas(@Param("cursoId") Long cursoId, @Param("desde") Instant desde);

    void deleteAllByModulo_IdIn(Collection<Long> moduloIds);

    // ---------------------------------------------------------------- HU-026 --

    /** Calendario del alumno: sesiones en vivo de sus cursos matriculados dentro de un rango. */
    @Query("""
            select l from Leccion l
            join fetch l.modulo m
            join fetch m.curso c
            where c.id in :cursoIds and l.tipo = 'EN_VIVO' and l.activo = true
              and l.fechaHoraInicio >= :desde and l.fechaHoraInicio < :hasta
            order by l.fechaHoraInicio asc
            """)
    List<Leccion> buscarSesionesEnVivoDeCursosEntre(
            @Param("cursoIds") Collection<Long> cursoIds,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Universo de sesiones en vivo de un curso (sin filtro de fecha), para calcular elegibilidad. */
    @Query("select l from Leccion l where l.modulo.curso.id = :cursoId and l.tipo = 'EN_VIVO' and l.activo = true")
    List<Leccion> buscarSesionesEnVivoDeCurso(@Param("cursoId") Long cursoId);

    /** Job de cierre: sesiones en vivo no canceladas ya finalizadas (fecha de fin pasada). */
    @Query("""
            select l from Leccion l
            join fetch l.modulo m
            join fetch m.curso c
            where l.tipo = 'EN_VIVO' and l.activo = true and l.estado <> 'CANCELADA'
              and l.fechaHoraFin is not null and l.fechaHoraFin <= :ahora
            """)
    List<Leccion> buscarSesionesFinalizadasNoCanceladas(@Param("ahora") Instant ahora);

    /** Job de recordatorio: sesiones en vivo no canceladas que inician dentro del rango dado. */
    @Query("""
            select l from Leccion l
            join fetch l.modulo m
            join fetch m.curso c
            where l.tipo = 'EN_VIVO' and l.activo = true and l.estado <> 'CANCELADA'
              and l.fechaHoraInicio >= :desde and l.fechaHoraInicio < :hasta
            """)
    List<Leccion> buscarSesionesEnVivoQueInicianEntre(
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
