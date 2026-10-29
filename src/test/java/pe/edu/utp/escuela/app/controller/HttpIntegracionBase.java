package pe.edu.utp.escuela.app.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EstadoCurso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.integration.CulqiIntegration;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.EstadoCursoRepository;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.ModuloRepository;
import pe.edu.utp.escuela.app.repository.PersonaRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las pruebas HTTP de las épicas 4, 5 y 6: levanta la aplicación completa con H2 (perfil {@code test}), pasa
 * por la cadena real de Spring Security (cookie/JWT, roles y CSRF) y reemplaza solo lo externo: el correo y Culqi.
 * Cada prueba corre en una transacción que se revierte al terminar, de modo que no deja datos en la base compartida.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class HttpIntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired WebApplicationContext contexto;
    @Autowired JsonMapper json;
    @Autowired EstadoCursoRepository estados;
    @Autowired CursoRepository cursos;
    @Autowired PersonaRepository personas;
    @Autowired UsuarioRepository usuarios;
    @Autowired MatriculaRepository matriculas;
    @Autowired ModuloRepository modulos;
    @Autowired LeccionRepository lecciones;
    @MockitoBean MailService correo;
    @MockitoBean CulqiIntegration culqi;

    MockMvc mvc;

    @BeforeEach
    void prepararMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    /** Sesión del alumno {@code id}, igual que la que construye la cookie firmada del backend. */
    static RequestPostProcessor comoAlumno(Long id) {
        return sesion(id, "ROLE_ALUMNO");
    }

    static RequestPostProcessor comoAdministrador(Long id) {
        return sesion(id, "ROLE_ADMINISTRADOR");
    }

    private static RequestPostProcessor sesion(Long id, String rol) {
        return jwt().jwt(token -> token.subject(String.valueOf(id)).claim("email", "usuario" + id + "@example.com"))
                .authorities(new SimpleGrantedAuthority(rol));
    }

    // ---------------------------------------------------------------- datos de prueba --

    static int siguiente() {
        return SECUENCIA.incrementAndGet();
    }

    EstadoCurso estado(String codigo) {
        return estados.findByCodigo(codigo).orElseGet(() -> {
            EstadoCurso estado = new EstadoCurso();
            estado.setCodigo(codigo);
            estado.setNombre(codigo);
            estado.setOrden(1);
            return estados.saveAndFlush(estado);
        });
    }

    Usuario alumno(String nombres) {
        int n = siguiente();
        Persona persona = new Persona();
        persona.setNombres(nombres);
        persona.setApellidoPaterno("Prueba");
        persona = personas.saveAndFlush(persona);
        Usuario usuario = new Usuario();
        usuario.setPersona(persona);
        usuario.setCorreo("alumno" + n + "@example.com");
        usuario.setOrigenRegistro("FORMULARIO");
        usuario.setActivo(true);
        usuario.setCorreoVerificadoEn(Instant.parse("2026-01-01T00:00:00Z"));
        usuario.setRequiereCambioContrasena(false);
        return usuarios.saveAndFlush(usuario);
    }

    Curso curso(String estadoCodigo, String modalidad, String precio) {
        int n = siguiente();
        Curso curso = new Curso();
        curso.setUrlAmigable("curso-prueba-" + n);
        curso.setTitulo("Curso de prueba " + n);
        curso.setEstadoCurso(estado(estadoCodigo));
        curso.setModalidad(modalidad);
        curso.setTipoVenta(new BigDecimal(precio).signum() == 0 ? "GRATUITO" : "PAGADO");
        curso.setPrecioRegular(new BigDecimal(precio));
        return cursos.saveAndFlush(curso);
    }

    Matricula matricula(Usuario usuario, Curso curso, String estado) {
        Matricula matricula = new Matricula();
        matricula.setUsuario(usuario);
        matricula.setCurso(curso);
        matricula.setEstado(estado);
        matricula.setFormaIngreso("PAGO_EN_LINEA");
        matricula.setFechaMatricula(Instant.parse("2026-01-02T00:00:00Z"));
        return matriculas.saveAndFlush(matricula);
    }

    Modulo modulo(Curso curso, int orden) {
        Modulo modulo = new Modulo();
        modulo.setCurso(curso);
        modulo.setTitulo("Módulo " + orden);
        modulo.setOrden(orden);
        return modulos.saveAndFlush(modulo);
    }

    Leccion leccion(Modulo modulo, int orden, String tipo) {
        Leccion leccion = new Leccion();
        leccion.setModulo(modulo);
        leccion.setTitulo("Lección " + orden);
        leccion.setOrden(orden);
        leccion.setTipo(tipo);
        leccion.setEstado("PROGRAMADA");
        return lecciones.saveAndFlush(leccion);
    }
}
