package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utp.escuela.app.dto.ActualizarPerfilRequest;
import pe.edu.utp.escuela.app.dto.CambiarContrasenaRequest;
import pe.edu.utp.escuela.app.dto.NuevaContrasenaRequest;
import pe.edu.utp.escuela.app.dto.PerfilResponse;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.InvalidCurrentPasswordException;
import pe.edu.utp.escuela.app.exception.OperationNotAllowedException;
import pe.edu.utp.escuela.app.exception.UnauthorizedException;
import pe.edu.utp.escuela.app.repository.PersonaRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTests {

    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private CurrentUserService currentUserService;
    @Mock private PasswordEncoder encoder;
    @Mock private PasswordPolicyService passwordPolicyService;

    private final TextNormalizer textNormalizer = new TextNormalizer();

    private PerfilService service;

    @BeforeEach
    void setUp() {
        service = new PerfilService(usuarios, personas, currentUserService, textNormalizer, encoder,
                passwordPolicyService);
    }

    private Usuario usuarioConPersona(Long id, Long personaId, String googleSubject, String contrasenaHash) {
        Persona persona = new Persona();
        persona.setId(personaId);
        persona.setNombres("Ana");
        persona.setApellidoPaterno("Pérez");
        persona.setApellidoMaterno("Ruiz");
        persona.setTelefono("999999999");
        persona.setDocumentoIdentidad("12345678");

        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPersona(persona);
        usuario.setCorreo("ana@example.com");
        usuario.setActivo(true);
        usuario.setGoogleSubject(googleSubject);
        usuario.setContrasenaHash(contrasenaHash);
        return usuario;
    }

    private void mockUsuarioActual(Long id, Usuario usuario) {
        when(currentUserService.get()).thenReturn(new CurrentUser(id, usuario.getCorreo(), java.util.Set.of()));
        when(usuarios.findByIdAndActivoTrue(id)).thenReturn(Optional.of(usuario));
    }

    @Test
    void obtenerDevuelveLosDatosDeLaPersonaYElCorreoDelUsuario() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash");
        mockUsuarioActual(1L, usuario);

        PerfilResponse respuesta = service.obtener();

        assertEquals("Ana", respuesta.nombres());
        assertEquals("ana@example.com", respuesta.correo());
        assertFalse(respuesta.accesoGoogle());
        assertFalse(respuesta.puedeCrearContrasena());
    }

    @Test
    void obtenerIndicaQueUnaCuentaGoogleSinContrasenaPuedeCrearUna() {
        Usuario usuario = usuarioConPersona(1L, 10L, "google-sub", null);
        mockUsuarioActual(1L, usuario);

        PerfilResponse respuesta = service.obtener();

        assertTrue(respuesta.accesoGoogle());
        assertTrue(respuesta.puedeCrearContrasena());
    }

    @Test
    void obtenerSinSesionLanzaNoAutorizado() {
        when(currentUserService.get()).thenThrow(new UnauthorizedException());

        assertThrows(UnauthorizedException.class, () -> service.obtener());
    }

    @Test
    void actualizarConDatosValidosModificaLaPersona() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash");
        mockUsuarioActual(1L, usuario);
        when(personas.existsByDocumentoIdentidadAndIdNot(anyString(), eq(10L))).thenReturn(false);

        PerfilResponse respuesta = service.actualizar(
                new ActualizarPerfilRequest("Ana María", "Gómez", "Ruiz", "988888888", "87654321"));

        assertEquals("Ana María", respuesta.nombres());
        assertEquals("Gómez", respuesta.apellidoPaterno());
        assertEquals("87654321", respuesta.documentoIdentidad());
    }

    @Test
    void actualizarConDocumentoYaUsadoPorOtraPersonaLanzaDuplicado() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash");
        mockUsuarioActual(1L, usuario);
        when(personas.existsByDocumentoIdentidadAndIdNot("87654321", 10L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.actualizar(
                new ActualizarPerfilRequest("Ana", "Pérez", "Ruiz", "999999999", "87654321")));
    }

    @Test
    void actualizarConElMismoDocumentoActualNoConsultaDuplicados() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash");
        mockUsuarioActual(1L, usuario);

        service.actualizar(new ActualizarPerfilRequest("Ana", "Pérez", "Ruiz", "999999999", "12345678"));

        verify(personas, never()).existsByDocumentoIdentidadAndIdNot(anyString(), eq(10L));
    }

    @Test
    void actualizarSinNombresLanzaValidacion() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash");
        mockUsuarioActual(1L, usuario);

        assertThrows(BusinessValidationException.class, () -> service.actualizar(
                new ActualizarPerfilRequest(" ", "Pérez", "Ruiz", "999999999", "12345678")));
    }

    @Test
    void crearContrasenaParaCuentaGoogleSinContrasenaLaEstablece() {
        Usuario usuario = usuarioConPersona(1L, 10L, "google-sub", null);
        mockUsuarioActual(1L, usuario);
        when(encoder.encode("Clave123")).thenReturn("hash-nuevo");

        service.crearContrasena(new NuevaContrasenaRequest("Clave123", "Clave123"));

        assertEquals("hash-nuevo", usuario.getContrasenaHash());
    }

    @Test
    void crearContrasenaParaCuentaSinGoogleLanzaOperacionNoPermitida() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash");
        mockUsuarioActual(1L, usuario);

        assertThrows(OperationNotAllowedException.class,
                () -> service.crearContrasena(new NuevaContrasenaRequest("Clave123", "Clave123")));
    }

    @Test
    void crearContrasenaParaCuentaGoogleQueYaTieneContrasenaLanzaOperacionNoPermitida() {
        Usuario usuario = usuarioConPersona(1L, 10L, "google-sub", "hash-existente");
        mockUsuarioActual(1L, usuario);

        assertThrows(OperationNotAllowedException.class,
                () -> service.crearContrasena(new NuevaContrasenaRequest("Clave123", "Clave123")));
    }

    @Test
    void crearContrasenaConConfirmacionDistintaLanzaValidacion() {
        Usuario usuario = usuarioConPersona(1L, 10L, "google-sub", null);
        mockUsuarioActual(1L, usuario);

        assertThrows(BusinessValidationException.class,
                () -> service.crearContrasena(new NuevaContrasenaRequest("Clave123", "Otra123")));
    }

    @Test
    void cambiarContrasenaConActualCorrectaLaReemplaza() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash-viejo");
        mockUsuarioActual(1L, usuario);
        when(encoder.matches("ActualClave1", "hash-viejo")).thenReturn(true);
        when(encoder.encode("NuevaClave1")).thenReturn("hash-nuevo");

        service.cambiarContrasena(
                new CambiarContrasenaRequest("ActualClave1", "NuevaClave1", "NuevaClave1"));

        assertEquals("hash-nuevo", usuario.getContrasenaHash());
    }

    @Test
    void cambiarContrasenaConActualIncorrectaLanzaCredencialInvalida() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash-viejo");
        mockUsuarioActual(1L, usuario);
        when(encoder.matches("Mala", "hash-viejo")).thenReturn(false);

        assertThrows(InvalidCurrentPasswordException.class, () -> service.cambiarContrasena(
                new CambiarContrasenaRequest("Mala", "NuevaClave1", "NuevaClave1")));
    }

    @Test
    void cambiarContrasenaSinContrasenaPreviaLanzaOperacionNoPermitida() {
        Usuario usuario = usuarioConPersona(1L, 10L, "google-sub", null);
        mockUsuarioActual(1L, usuario);

        assertThrows(OperationNotAllowedException.class, () -> service.cambiarContrasena(
                new CambiarContrasenaRequest("Cualquiera1", "NuevaClave1", "NuevaClave1")));
    }

    @Test
    void cambiarContrasenaConConfirmacionDistintaLanzaValidacion() {
        Usuario usuario = usuarioConPersona(1L, 10L, null, "hash-viejo");
        mockUsuarioActual(1L, usuario);
        when(encoder.matches("ActualClave1", "hash-viejo")).thenReturn(true);

        assertThrows(BusinessValidationException.class, () -> service.cambiarContrasena(
                new CambiarContrasenaRequest("ActualClave1", "NuevaClave1", "OtraClave1")));
    }
}
