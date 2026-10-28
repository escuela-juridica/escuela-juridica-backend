package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utp.escuela.app.dto.AccesoPeticion;
import pe.edu.utp.escuela.app.dto.ResultadoAcceso;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.InactiveAccountException;
import pe.edu.utp.escuela.app.exception.InvalidCredentialsException;
import pe.edu.utp.escuela.app.exception.PendingEmailVerificationException;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepository;
import pe.edu.utp.escuela.app.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AccesoServiceTests {

    @Mock private UsuarioRepository usuarios;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtService jwtService;

    private AccesoService service;

    @BeforeEach
    void setUp() {
        service = new AccesoService(usuarios, usuarioRoles, encoder, jwtService);
    }

    private Usuario usuarioActivo(Long id, String hash, boolean verificado, boolean requiereCambio) {
        Persona persona = new Persona();
        persona.setNombres("Ana");
        persona.setApellidoPaterno("Pérez");

        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPersona(persona);
        usuario.setCorreo("ana@example.com");
        usuario.setActivo(true);
        usuario.setContrasenaHash(hash);
        usuario.setRequiereCambioContrasena(requiereCambio);
        if (verificado) {
            usuario.setCorreoVerificadoEn(Instant.parse("2026-08-01T00:00:00Z"));
        }
        return usuario;
    }

    private AccesoPeticion peticion(String correo, String contrasena) {
        return new AccesoPeticion(correo, contrasena);
    }

    @Test
    void accederConCredencialesCorrectasEmiteJwtYSesion() {
        Usuario usuario = usuarioActivo(1L, "hash-real", true, false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(encoder.matches("Clave123", "hash-real")).thenReturn(true);
        when(usuarioRoles.buscarCodigosRolesPrincipales(1L)).thenReturn(List.of("ROLE_ALUMNO"));
        when(jwtService.issue(eq(1L), eq("ana@example.com"), eq(List.of("ALUMNO"))))
                .thenReturn("jwt-emitido");

        ResultadoAcceso resultado = service.acceder(peticion("ANA@example.com", "Clave123"));

        assertEquals("jwt-emitido", resultado.jwt());
        assertEquals("ALUMNO", resultado.sesion().rolPrincipal());
        assertEquals("Ana Pérez", resultado.sesion().nombreCompleto());
        assertFalse(resultado.sesion().requiereCambioContrasena());
    }

    @Test
    void accederDistingueRolAdministrador() {
        Usuario usuario = usuarioActivo(2L, "hash-real", true, false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(encoder.matches(anyString(), eq("hash-real"))).thenReturn(true);
        when(usuarioRoles.buscarCodigosRolesPrincipales(2L)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
        when(jwtService.issue(eq(2L), anyString(), eq(List.of("ADMINISTRADOR")))).thenReturn("jwt");

        ResultadoAcceso resultado = service.acceder(peticion("ana@example.com", "Clave123"));

        assertEquals("ADMINISTRADOR", resultado.sesion().rolPrincipal());
    }

    @Test
    void accederConCorreoInexistenteLanzaCredencialesInvalidas() {
        when(usuarios.findByCorreoIgnoreCase("nadie@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.acceder(peticion("nadie@example.com", "loquesea")));
    }

    @Test
    void accederConContrasenaIncorrectaLanzaElMismoErrorQueCorreoInexistente() {
        Usuario usuario = usuarioActivo(1L, "hash-real", true, false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(encoder.matches("mala", "hash-real")).thenReturn(false);

        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class,
                () -> service.acceder(peticion("ana@example.com", "mala")));

        assertEquals("INVALID_CREDENTIALS", error.getCode());
    }

    @Test
    void accederConCuentaInactivaLanzaExcepcionDedicada() {
        Usuario usuario = usuarioActivo(1L, "hash-real", true, false);
        usuario.setActivo(false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));

        assertThrows(InactiveAccountException.class,
                () -> service.acceder(peticion("ana@example.com", "Clave123")));
    }

    @Test
    void accederConCorreoPendienteLanzaExcepcionDedicadaSinValidarContrasena() {
        Usuario usuario = usuarioActivo(1L, "hash-real", false, false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(encoder.matches("Clave123", "hash-real")).thenReturn(true);

        assertThrows(PendingEmailVerificationException.class,
                () -> service.acceder(peticion("ana@example.com", "Clave123")));
    }

    @Test
    void accederSinRolPrincipalLanzaEstadoInconsistente() {
        Usuario usuario = usuarioActivo(1L, "hash-real", true, false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(encoder.matches("Clave123", "hash-real")).thenReturn(true);
        when(usuarioRoles.buscarCodigosRolesPrincipales(1L)).thenReturn(List.of());

        assertThrows(IllegalStateException.class,
                () -> service.acceder(peticion("ana@example.com", "Clave123")));
    }

    @Test
    void obtenerSesionDevuelveElPerfilMinimoSinDatosSensibles() {
        Usuario usuario = usuarioActivo(1L, "hash-real", true, true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRoles.buscarCodigosRolesPrincipales(1L)).thenReturn(List.of("ROLE_ALUMNO"));

        var sesion = service.obtenerSesion(1L);

        assertEquals(1L, sesion.usuarioId());
        assertEquals("ana@example.com", sesion.correo());
        assertTrue(sesion.requiereCambioContrasena());
    }
}
