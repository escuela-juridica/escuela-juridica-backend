package pe.edu.utp.escuela.app.repository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.entity.Usuario;
public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    boolean existsByCorreoIgnoreCase(String correo);
    Optional<Usuario> findByCorreoIgnoreCase(String correo);
    Optional<Usuario> findByGoogleSubject(String subject);

    @EntityGraph(attributePaths = "persona")
    Optional<Usuario> findByIdAndActivoTrue(Long id);

    @EntityGraph(attributePaths = "persona")
    Optional<Usuario> findWithPersonaById(Long id);

    @Query(value = """
            select u from Usuario u join fetch u.persona p
            where (:texto = ''
                   or lower(p.nombres) like concat('%', :texto, '%')
                   or lower(p.apellidoPaterno) like concat('%', :texto, '%')
                   or lower(coalesce(p.apellidoMaterno, '')) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%'))
              and (:activo is null or u.activo = :activo)
              and (:rol = '' or exists (
                    select 1 from UsuarioRol ur join Rol r on r.id = ur.id.rolId
                    where ur.id.usuarioId = u.id and r.codigo = :rol))
            order by u.creadoEn desc
            """,
            countQuery = """
            select count(u) from Usuario u
            where (:texto = ''
                   or lower(u.persona.nombres) like concat('%', :texto, '%')
                   or lower(u.persona.apellidoPaterno) like concat('%', :texto, '%')
                   or lower(coalesce(u.persona.apellidoMaterno, '')) like concat('%', :texto, '%')
                   or lower(u.correo) like concat('%', :texto, '%'))
              and (:activo is null or u.activo = :activo)
              and (:rol = '' or exists (
                    select 1 from UsuarioRol ur join Rol r on r.id = ur.id.rolId
                    where ur.id.usuarioId = u.id and r.codigo = :rol))
            """)
    Page<Usuario> buscarAdministrativos(
            @Param("texto") String texto,
            @Param("activo") Boolean activo,
            @Param("rol") String rol,
            Pageable pageable);
}

