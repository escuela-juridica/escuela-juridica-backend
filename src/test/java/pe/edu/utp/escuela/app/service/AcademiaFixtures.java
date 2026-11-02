package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Recurso;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.service.RutaAcademicaService.RutaAcademica;

/** Datos de prueba compartidos por las pruebas de las épicas 4, 5 y 6 (aula, certificación y pagos). */
final class AcademiaFixtures {

    static final Instant AHORA = Instant.parse("2026-09-15T12:00:00Z");

    private AcademiaFixtures() {
    }

    static Usuario alumno(Long id) {
        Persona persona = new Persona();
        persona.setNombres("Ana");
        persona.setApellidoPaterno("Pérez");
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPersona(persona);
        usuario.setCorreo("ana@example.com");
        usuario.setActivo(true);
        usuario.setCorreoVerificadoEn(Instant.parse("2026-08-01T00:00:00Z"));
        usuario.setRequiereCambioContrasena(false);
        return usuario;
    }

    static Curso curso(Long id, String estadoCodigo, String modalidad) {
        EstadoCurso estado = new EstadoCurso();
        estado.setCodigo(estadoCodigo);
        Curso curso = new Curso();
        curso.setId(id);
        curso.setTitulo("Derecho Procesal Penal");
        curso.setEstadoCurso(estado);
        curso.setModalidad(modalidad);
        curso.setPrecioRegular(BigDecimal.valueOf(320));
        curso.setFechaInicio(LocalDate.of(2026, 9, 1));
        curso.setFechaFin(LocalDate.of(2026, 12, 31));
        return curso;
    }

    static Matricula matricula(Long id, Usuario usuario, Curso curso, String estado) {
        Matricula matricula = new Matricula();
        matricula.setId(id);
        matricula.setUsuario(usuario);
        matricula.setCurso(curso);
        matricula.setEstado(estado);
        matricula.setFechaMatricula(Instant.parse("2026-08-20T00:00:00Z"));
        return matricula;
    }

    static Modulo modulo(Long id, Curso curso, int orden) {
        Modulo modulo = new Modulo();
        modulo.setId(id);
        modulo.setCurso(curso);
        modulo.setTitulo("Módulo " + orden);
        modulo.setOrden(orden);
        return modulo;
    }

    static Leccion leccion(Long id, Modulo modulo, int orden, String tipo, boolean obligatoria) {
        Leccion leccion = new Leccion();
        leccion.setId(id);
        leccion.setModulo(modulo);
        leccion.setTitulo("Lección " + id);
        leccion.setOrden(orden);
        leccion.setTipo(tipo);
        leccion.setEstado("PROGRAMADA");
        leccion.setEsObligatoria(obligatoria);
        return leccion;
    }

    static Recurso recurso(Long id, String tipo, String origen) {
        Recurso recurso = new Recurso();
        recurso.setId(id);
        recurso.setTipo(tipo);
        recurso.setOrigen(origen);
        recurso.setReferencia("SUBIDO".equals(origen) ? "videos/clase-" + id + ".mp4" : "https://youtu.be/clase-" + id);
        recurso.setNombreArchivo("clase-" + id + ".mp4");
        recurso.setTipoMime("video/mp4");
        return recurso;
    }

    static MaterialLeccion material(Long id, Leccion leccion, Recurso recurso, boolean permiteDescarga) {
        MaterialLeccion material = new MaterialLeccion();
        material.setId(id);
        material.setLeccion(leccion);
        material.setRecurso(recurso);
        material.setTitulo("Material " + id);
        material.setOrden(1);
        material.setPermiteDescarga(permiteDescarga);
        material.setActivo(true);
        return material;
    }

    static Examen examen(Long id, Curso curso, Modulo modulo, String tipo, String finalidad) {
        Examen examen = new Examen();
        examen.setId(id);
        examen.setCurso(curso);
        examen.setModulo(modulo);
        examen.setTitulo("Examen " + id);
        examen.setTipo(tipo);
        examen.setFinalidad(finalidad);
        examen.setOrden(1);
        return examen;
    }

    /** Ruta ya calculada con las lecciones dadas (en orden) y cuáles están completadas. */
    static RutaAcademica ruta(ReglaCurso regla, List<Modulo> modulos, List<Leccion> lecciones,
            Map<Long, Boolean> completadas, Map<Long, String> bloqueos, List<Examen> calificados,
            Map<Long, BigDecimal> mejoresNotas, java.util.Set<Long> aprobados) {
        Map<Long, List<Leccion>> porModulo = new LinkedHashMap<>();
        for (Modulo modulo : modulos) {
            porModulo.put(modulo.getId(), lecciones.stream()
                    .filter(l -> l.getModulo().getId().equals(modulo.getId())).toList());
        }
        return new RutaAcademica(regla, modulos, porModulo, lecciones, completadas, bloqueos, new HashMap<>(),
                List.of(), calificados, new HashSet<>(aprobados), mejoresNotas, new LinkedHashMap<>());
    }
}
