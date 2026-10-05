package pe.edu.utp.escuela.app.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utp.escuela.app.adminusuario.UsuarioRolFila;
import pe.edu.utp.escuela.app.entity.UsuarioRol;

public interface UsuarioRolRepositorio extends JpaRepository<UsuarioRol, UsuarioRol.Clave> {

    @Query("""
            select r.codigo
            from UsuarioRol ur, Rol r
            where ur.id.usuarioId = :usuarioId
              and ur.id.rolId = r.id
              and ur.principal = true
              and r.activo = true
            """)
    List<String> buscarCodigosRolesPrincipales(@Param("usuarioId") Long usuarioId);

    @Query("""
            select new pe.edu.utp.escuela.app.adminusuario.UsuarioRolFila(
                ur.id.usuarioId, r.codigo, ur.principal, ur.asignadoPorUsuarioId, ur.asignadoEn)
            from UsuarioRol ur join Rol r on r.id = ur.id.rolId
            where ur.id.usuarioId in :usuarioIds
            """)
    List<UsuarioRolFila> buscarPorUsuarios(@Param("usuarioIds") Collection<Long> usuarioIds);

    @Query("""
            select new pe.edu.utp.escuela.app.adminusuario.UsuarioRolFila(
                ur.id.usuarioId, r.codigo, ur.principal, ur.asignadoPorUsuarioId, ur.asignadoEn)
            from UsuarioRol ur join Rol r on r.id = ur.id.rolId
            where ur.id.usuarioId = :usuarioId
            """)
    List<UsuarioRolFila> buscarPorUsuario(@Param("usuarioId") Long usuarioId);

    @Query("""
            select count(distinct ur.id.usuarioId) from UsuarioRol ur
            join Rol r on r.id = ur.id.rolId
            join Usuario u on u.id = ur.id.usuarioId
            where r.codigo = :codigoRol and u.activo = true
            """)
    long contarActivosConRol(@Param("codigoRol") String codigoRol);
}
