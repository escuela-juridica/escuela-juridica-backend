package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.ConteoMatriculaRow;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaResponse;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaResponse;
import pe.edu.utp.escuela.app.entity.Matricula;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    boolean existsByCurso_Id(Long cursoId);

    boolean existsByUsuario_IdAndCurso_Id(Long usuarioId, Long cursoId);

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    Optional<Matricula> findByUsuario_IdAndCurso_Id(Long usuarioId, Long cursoId);

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    List<Matricula> findByUsuario_IdOrderByFechaMatriculaDesc(Long usuarioId);

    @EntityGraph(attributePaths = { "curso", "usuario", "usuario.persona" })
    List<Matricula> findAllByOrderByFechaMatriculaDesc();

    @EntityGraph(attributePaths = { "usuario" })
    List<Matricula> findByEstadoAndCurso_Id(String estado, Long cursoId);

    long countByCurso_IdAndEstado(Long cursoId, String estado);

    interface ActivacionRow {
        Long getId();

        Instant getFecha();

        Long getCursoId();

        String getCurso();

        String getForma();
    }

    @Query("""
            select m.id as id, m.fechaActivacion as fecha, c.id as cursoId, c.titulo as curso, m.formaIngreso as forma
            from Matricula m join m.curso c
            where m.fechaActivacion is not null
              and m.fechaActivacion >= :desde and m.fechaActivacion < :hasta
              and (:cursoId is null or c.id = :cursoId)
              and (:modalidad = '' or c.modalidad = :modalidad)
            """)
    List<ActivacionRow> activacionesParaDashboard(@Param("cursoId") Long cursoId, @Param("modalidad") String modalidad,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    List<Matricula> findByCurso_IdAndUsuario_IdIn(Long cursoId, java.util.Collection<Long> usuarioIds);

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    List<Matricula> findByEstadoAndFechaVencimientoLessThanEqual(String estado, Instant instante);

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    Optional<Matricula> findWithDetalleById(Long id);

    @Query(value = """
            select new pe.edu.utp.escuela.app.dto.MatriculaAdministrativaResponse(
                m.id, u.id, concat(p.nombres, ' ', p.apellidoPaterno), u.correo,
                c.id, c.titulo, m.estado, m.formaIngreso, m.fechaMatricula, m.fechaVencimiento)
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            where (:estado = '' or m.estado = :estado)
              and (:usuarioId is null or u.id = :usuarioId)
              and (:cursoId is null or c.id = :cursoId)
            order by m.fechaMatricula desc
            """,
            countQuery = """
            select count(m)
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            where (:estado = '' or m.estado = :estado)
              and (:usuarioId is null or u.id = :usuarioId)
              and (:cursoId is null or c.id = :cursoId)
            """)
    Page<MatriculaAdministrativaResponse> buscarAdministrativas(
            @Param("usuarioId") Long usuarioId, @Param("cursoId") Long cursoId, @Param("estado") String estado,
            Pageable pageable);

    @Query(value = """
            select new pe.edu.utp.escuela.app.dto.ReporteMatriculaResponse(
                m.id, concat(p.nombres, ' ', p.apellidoPaterno), u.correo, c.titulo, c.modalidad,
                m.fechaMatricula, m.fechaActivacion, m.estado, m.formaIngreso,
                case when m.fechaFinalizacion is null then 'EN_CURSO' else 'FINALIZADO' end,
                coalesce(certificado.estado, 'NO_EMITIDO'))
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            left join m.logroCertificacion logro
            left join Certificado certificado on certificado.logro = logro
                and certificado.id = (select max(k.id) from Certificado k where k.logro = logro)
            where (:estado = '' or m.estado = :estado)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
              and (:cursoId is null or c.id = :cursoId)
              and (:modalidad = '' or c.modalidad = :modalidad)
              and m.fechaMatricula >= :desde
              and m.fechaMatricula < :hasta
            order by m.fechaMatricula desc
            """,
            countQuery = """
            select count(m)
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            where (:estado = '' or m.estado = :estado)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
              and (:cursoId is null or c.id = :cursoId)
              and (:modalidad = '' or c.modalidad = :modalidad)
              and m.fechaMatricula >= :desde
              and m.fechaMatricula < :hasta
            """)
    Page<ReporteMatriculaResponse> buscarReporte(
            @Param("texto") String texto, @Param("estado") String estado,
            @Param("cursoId") Long cursoId, @Param("modalidad") String modalidad,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta, Pageable pageable);

    @Query("""
            select new pe.edu.utp.escuela.app.dto.ReporteMatriculaResponse(
                m.id, concat(p.nombres, ' ', p.apellidoPaterno), u.correo, c.titulo, c.modalidad,
                m.fechaMatricula, m.fechaActivacion, m.estado, m.formaIngreso,
                case when m.fechaFinalizacion is null then 'EN_CURSO' else 'FINALIZADO' end,
                coalesce(certificado.estado, 'NO_EMITIDO'))
            from Matricula m
            join m.usuario u join u.persona p join m.curso c
            left join m.logroCertificacion logro
            left join Certificado certificado on certificado.logro = logro
                and certificado.id = (select max(k.id) from Certificado k where k.logro = logro)
            where (:estado = '' or m.estado = :estado)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
              and (:cursoId is null or c.id = :cursoId)
              and (:modalidad = '' or c.modalidad = :modalidad)
              and m.fechaMatricula >= :desde
              and m.fechaMatricula < :hasta
            order by m.fechaMatricula desc
            """)
    List<ReporteMatriculaResponse> exportarReporte(
            @Param("texto") String texto, @Param("estado") String estado,
            @Param("cursoId") Long cursoId, @Param("modalidad") String modalidad,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("""
            select new pe.edu.utp.escuela.app.dto.ConteoMatriculaRow(m.curso.id, count(m.id))
            from Matricula m
            where m.curso.id in :cursoIds and m.estado = 'ACTIVA'
            group by m.curso.id
            """)
    List<ConteoMatriculaRow> contarActivasPorCurso(@Param("cursoIds") Collection<Long> cursoIds);

    default Map<Long, Long> contarActivas(Collection<Long> cursoIds) {
        if (cursoIds.isEmpty()) {
            return Map.of();
        }
        return contarActivasPorCurso(cursoIds).stream()
                .collect(Collectors.toMap(ConteoMatriculaRow::cursoId, ConteoMatriculaRow::activas));
    }

    String ALCANCE_ACADEMICO = """
            from Matricula m
            join m.curso c join m.usuario u join u.persona p
            where m.estado in ('ACTIVA', 'VENCIDA', 'FINALIZADA', 'CANCELADA')
              and (:cursoId is null or c.id = :cursoId)
              and (:modalidad = '' or c.modalidad = :modalidad)
              and (:texto = '' or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%')
                   or lower(concat(p.nombres, ' ', p.apellidoPaterno)) like concat('%', :texto, '%'))
              and m.fechaMatricula >= :desde and m.fechaMatricula < :hasta
            """;

    String ORDEN_ACADEMICO = " order by p.apellidoPaterno asc, p.nombres asc, c.titulo asc, m.id asc";

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    @Query(value = "select m " + ALCANCE_ACADEMICO + ORDEN_ACADEMICO, countQuery = "select count(m) " + ALCANCE_ACADEMICO)
    Page<Matricula> buscarReporteAcademico(
            @Param("texto") String texto, @Param("cursoId") Long cursoId, @Param("modalidad") String modalidad,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta, Pageable pageable);

    String ALCANCE_GESTION_CERTIFICADOS = """
            from Matricula m join m.usuario u join u.persona p join m.curso c
            where m.estado <> 'PENDIENTE_PAGO'
              and (:usuarioId is null or u.id = :usuarioId)
              and (:cursoId is null or c.id = :cursoId)
              and (:estadoCertificado = ''
                   or (:estadoCertificado = 'SIN_CERTIFICADO'
                       and not exists (select 1 from Certificado k where k.logro.matricula = m))
                   or (:estadoCertificado = 'VIGENTE'
                       and exists (select 1 from Certificado k where k.logro.matricula = m and k.estado = 'VIGENTE'))
                   or (:estadoCertificado = 'ANULADO'
                       and exists (select 1 from Certificado k where k.logro.matricula = m and k.estado = 'ANULADO')
                       and not exists (select 1 from Certificado k where k.logro.matricula = m and k.estado = 'VIGENTE')))
            """;

    @EntityGraph(attributePaths = { "curso", "usuario", "usuario.persona" })
    @Query(value = "select m " + ALCANCE_GESTION_CERTIFICADOS + " order by m.fechaMatricula desc, m.id desc",
            countQuery = "select count(m) " + ALCANCE_GESTION_CERTIFICADOS)
    Page<Matricula> buscarGestionCertificados(@Param("usuarioId") Long usuarioId, @Param("cursoId") Long cursoId,
            @Param("estadoCertificado") String estadoCertificado, Pageable pageable);

    @EntityGraph(attributePaths = { "curso", "curso.estadoCurso", "usuario", "usuario.persona" })
    @Query("select m " + ALCANCE_ACADEMICO + ORDEN_ACADEMICO)
    List<Matricula> exportarReporteAcademico(
            @Param("texto") String texto, @Param("cursoId") Long cursoId, @Param("modalidad") String modalidad,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
