package pe.edu.utp.escuela.app.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.escuela.app.entity.Persona;
public interface PersonaRepositorio extends JpaRepository<Persona, Long> {
    boolean existsByDocumentoIdentidad(String documentoIdentidad);
    boolean existsByDocumentoIdentidadAndIdNot(String documentoIdentidad, Long id);

    /** "Docente" no es un rol de acceso: es cualquier persona con cargo profesional publicado
     * (ver HU-009). */
    Page<Persona> findByCargoProfesionalIsNotNullAndActivoTrueOrderByNombresAsc(Pageable pageable);
    Page<Persona> findByCargoProfesionalIsNotNullOrderByNombresAsc(Pageable pageable);
}
