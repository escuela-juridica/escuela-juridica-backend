package pe.edu.utp.escuela.app.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.MatriculaAlcanceFila;
import pe.edu.utp.escuela.app.dto.ReporteAsistenciaFila;
import pe.edu.utp.escuela.app.entity.Asistencia;

public interface AsistenciaRepositorio extends JpaRepository<Asistencia, Long> {

    /**
     * HU-045 — Universo y filtros del reporte, compartidos por la página, el conteo y la
     * exportación para que pantalla y Excel nunca discrepen: un registro por alumno y sesión en
     * vivo, de cursos EN_VIVO/HIBRIDO, solo sesiones NO canceladas y POSTERIORES a la matrícula
     * (el mismo denominador que usa el porcentaje de asistencia de HU-026).
     */
    String ALCANCE_REPORTE = """
            from Leccion l
            join l.modulo mo join mo.curso c
            join Matricula m on m.curso = c
            join m.usuario u join u.persona p
            left join Asistencia a on a.matricula = m and a.leccion = l
            where c.modalidad in ('EN_VIVO', 'HIBRIDO')
              and l.tipo = 'EN_VIVO' and l.activo = true and mo.activo = true
              and l.estado <> 'CANCELADA'
              and l.fechaHoraInicio is not null
              and m.estado in ('ACTIVA', 'VENCIDA')
              and l.fechaHoraInicio > m.fechaMatricula
              and (:cursoId is null or c.id = :cursoId)
              and (:sesionId is null or l.id = :sesionId)
              and (:modalidad = '' or c.modalidad = :modalidad)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
              and l.fechaHoraInicio >= :desde and l.fechaHoraInicio < :hasta
              and (:asistencia = '' or coalesce(a.estado, 'PENDIENTE') = :asistencia)
              and (:origen = ''
                   or (:origen = 'AUTOMATICA' and a.id is not null and a.registradaPorUsuarioId is null)
                   or (:origen = 'CORRECCION_ADMINISTRATIVA' and a.registradaPorUsuarioId is not null))
              and (:restringir = false or m.id in :matriculaIds)
            """;

    String SELECCION_REPORTE = """
            select new pe.edu.utp.escuela.app.dto.ReporteAsistenciaFila(
                m.id, c.id, c.titulo, c.modalidad, l.id, l.titulo, l.fechaHoraInicio,
                concat(p.nombres, ' ', p.apellidoPaterno), u.correo,
                coalesce(a.estado, 'PENDIENTE'),
                case when a.id is null then null
                     when a.registradaPorUsuarioId is null then 'AUTOMATICA'
                     else 'CORRECCION_ADMINISTRATIVA' end,
                a.motivoCorreccion)
            """;

    String ORDEN_REPORTE = " order by l.fechaHoraInicio desc, c.titulo asc, p.apellidoPaterno asc, "
            + "p.nombres asc, m.id asc";

    Optional<Asistencia> findByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);

    List<Asistencia> findByMatricula_Id(Long matriculaId);

    List<Asistencia> findByLeccion_Id(Long leccionId);

    boolean existsByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);

    /** HU-027: asistencia de una sesión con el alumno ya resuelto, para el modal "Corregir". */
    @Query("""
            select a from Asistencia a
            join fetch a.matricula m
            join fetch m.usuario u
            join fetch u.persona p
            where a.leccion.id = :leccionId
            order by p.apellidoPaterno asc, p.nombres asc
            """)
    List<Asistencia> buscarDeLeccionConAlumno(@Param("leccionId") Long leccionId);

    @Query(value = SELECCION_REPORTE + ALCANCE_REPORTE + ORDEN_REPORTE,
            countQuery = "select count(l) " + ALCANCE_REPORTE)
    Page<ReporteAsistenciaFila> buscarReporte(
            @Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("sesionId") Long sesionId, @Param("modalidad") String modalidad,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta,
            @Param("asistencia") String asistencia, @Param("origen") String origen,
            @Param("restringir") boolean restringir,
            @Param("matriculaIds") Collection<Long> matriculaIds, Pageable pageable);

    @Query(SELECCION_REPORTE + ALCANCE_REPORTE + ORDEN_REPORTE)
    List<ReporteAsistenciaFila> exportarReporte(
            @Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("sesionId") Long sesionId, @Param("modalidad") String modalidad,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta,
            @Param("asistencia") String asistencia, @Param("origen") String origen,
            @Param("restringir") boolean restringir,
            @Param("matriculaIds") Collection<Long> matriculaIds);

    /** HU-045: matrículas en alcance del reporte (por curso, modalidad y texto), sobre las que se
     * calcula el cumplimiento del mínimo antes de filtrar filas por cumplimiento. */
    @Query("""
            select distinct new pe.edu.utp.escuela.app.dto.MatriculaAlcanceFila(m.id, c.id) from Matricula m
            join m.curso c join m.usuario u join u.persona p
            where c.modalidad in ('EN_VIVO', 'HIBRIDO') and m.estado in ('ACTIVA', 'VENCIDA')
              and (:cursoId is null or c.id = :cursoId)
              and (:modalidad = '' or c.modalidad = :modalidad)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
            """)
    List<MatriculaAlcanceFila> matriculasEnAlcance(
            @Param("texto") String texto, @Param("cursoId") Long cursoId,
            @Param("modalidad") String modalidad);
}
