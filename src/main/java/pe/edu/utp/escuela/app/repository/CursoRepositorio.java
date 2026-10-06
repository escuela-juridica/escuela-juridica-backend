package pe.edu.utp.escuela.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.dto.CursoTarjetaFila;
import pe.edu.utp.escuela.app.entity.Curso;

public interface CursoRepositorio extends JpaRepository<Curso, Long> {

    @EntityGraph(attributePaths = {
            "tipoCurso", "categoriaTematica", "entidadCertificadora", "estadoCurso"
    })
    @Query("""
            select c from Curso c join c.estadoCurso e
            where c.urlAmigable = :slug and c.publicadoEn is not null
              and e.codigo not in ('BORRADOR', 'CANCELADO')
            """)
    Optional<Curso> buscarFichaPublica(@Param("slug") String slug);

    boolean existsByUrlAmigable(String urlAmigable);

    boolean existsByUrlAmigableAndIdNot(String urlAmigable, Long id);

    /** HU-016 — Candidatos a pasar de PUBLICADO a EN_CURSO: llegó su fecha de inicio. */
    List<Curso> findByEstadoCurso_CodigoAndFechaInicioLessThanEqual(String codigoEstado, LocalDate hoy);

    /** HU-016 — Candidatos a pasar de EN_CURSO a CERRADO: ya pasó su fecha de fin (nunca aplica a
     * VIRTUAL, que no tiene fecha de fin). */
    List<Curso> findByEstadoCurso_CodigoAndFechaFinLessThan(String codigoEstado, LocalDate hoy);

    @EntityGraph(attributePaths = {
            "tipoCurso", "categoriaTematica", "entidadCertificadora", "estadoCurso"
    })
    Optional<Curso> findWithDetalleById(Long id);

    @EntityGraph(attributePaths = { "tipoCurso", "categoriaTematica", "estadoCurso" })
    @Query("""
            select c from Curso c
            where (:texto = '' or lower(c.titulo) like concat('%', :texto, '%'))
            order by c.creadoEn desc
            """)
    Page<Curso> buscarAdministrativos(@Param("texto") String texto, Pageable pageable);

    /** Beneficios ya usados en otros cursos, para sugerir mientras se escribe uno nuevo (no hay
     * tabla maestra de beneficios: siguen siendo texto libre por curso, pero reutilizar redacciones
     * ya existentes evita variantes casi idénticas una al lado de la otra). */
    @Query(value = """
            select distinct beneficio
            from curso c, unnest(c.beneficios) as beneficio
            where (:texto = '' or lower(beneficio) like concat('%', :texto, '%'))
            order by beneficio
            limit 10
            """, nativeQuery = true)
    List<String> buscarBeneficiosSugeridos(@Param("texto") String texto);

    @Query(value = """
            select new pe.edu.utp.escuela.app.dto.CursoTarjetaFila(
                c.id, c.urlAmigable, c.titulo, c.descripcion, c.imagenPortadaUrl,
                c.modalidad, c.tipoVenta, c.destacado, c.precioRegular,
                c.precioPromocional, c.promocionInicioEn, c.promocionFinEn,
                c.fechaInicio, c.fechaFin, c.fechaCierreMatricula, c.cupoMaximo,
                c.horasAcademicas, t.codigo, t.nombre, ca.codigo, ca.nombre, e.codigo)
            from Curso c
            left join c.tipoCurso t
            left join c.categoriaTematica ca
            join c.estadoCurso e
            where c.publicadoEn is not null
              and e.codigo not in ('BORRADOR', 'CANCELADO')
              and (:texto = ''
                   or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(coalesce(c.descripcion, '')) like concat('%', :texto, '%'))
              and (:tipo = '' or t.codigo = :tipo)
              and (:categoria = '' or ca.codigo = :categoria)
            order by case when c.destacado = true then 0 else 1 end,
                     case when c.fechaInicio is null then 0
                          when c.fechaInicio >= :hoy then 1
                          else 2 end,
                     c.fechaInicio asc,
                     c.id asc
            """,
            countQuery = """
            select count(c.id)
            from Curso c
            left join c.tipoCurso t
            left join c.categoriaTematica ca
            join c.estadoCurso e
            where c.publicadoEn is not null
              and e.codigo not in ('BORRADOR', 'CANCELADO')
              and (:texto = ''
                   or lower(c.titulo) like concat('%', :texto, '%')
                   or lower(coalesce(c.descripcion, '')) like concat('%', :texto, '%'))
              and (:tipo = '' or t.codigo = :tipo)
              and (:categoria = '' or ca.codigo = :categoria)
            """)
    Page<CursoTarjetaFila> buscarPublicados(
            @Param("texto") String texto,
            @Param("tipo") String tipo,
            @Param("categoria") String categoria,
            @Param("hoy") LocalDate hoy,
            Pageable pageable);
}
