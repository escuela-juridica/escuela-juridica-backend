package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utp.escuela.app.dto.NuevaContrasenaRequest;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.TokenRecuperacionAcceso;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.InvalidTokenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.TokenRecuperacionAccesoRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.HashTokenService;
import pe.edu.utp.escuela.app.security.TokenAleatorioService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@ExtendWith(MockitoExtension.class)
class RecuperacionAccesoServiceTests {

    @Mock private UsuarioRepository usuarios;
    @Mock private TokenRecuperacionAccesoRepository repository;
    @Mock private TokenAleatorioService tokenAleatorioService;
    @Mock private HashTokenService hashTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private MailService mailService;

    private RecuperacionAccesoService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneId.of("America/Lima"));
        service = new RecuperacionAccesoService(
                usuarios, repository, tokenAleatorioService, hashTokenService, passwordEncoder,
                passwordPolicyService, mailService, new TextNormalizer(), clock,
                "http://localhost:4200");
    }

    private Usuario usuarioRecuperable(Long id, String hash) {
        Persona persona = new Persona();
        persona.setNombres("Ana");
        persona.setApellidoPaterno("Pérez");
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPersona(persona);
        usuario.setCorreo("ana@example.com");
        usuario.setActivo(true);
        usuario.setContrasenaHash(hash);
        return usuario;
    }

    private TokenRecuperacionAcceso tokenVigente(Long id, Usuario usuario) {
        TokenRecuperacionAcceso token = new TokenRecuperacionAcceso();
        token.setId(id);
        token.setUsuario(usuario);
        token.setTokenHash("hash-token");
        token.setSolicitadoEn(Instant.parse("2026-09-09T11:00:00Z"));
        token.setExpiraEn(Instant.parse("2026-09-09T12:30:00Z"));
        return token;
    }

    @Test
    void solicitarConCorreoInexistenteNoHaceNadaNiLanzaExcepcion() {
        when(usuarios.findByCorreoIgnoreCase("nadie@example.com")).thenReturn(Optional.empty());

        service.solicitar("nadie@example.com");

        verify(repository, never()).invalidarPendientes(anyLong(), any());
        verify(mailService, never()).sendHtml(any());
    }

    @Test
    void solicitarConCuentaInactivaNoEnviaNada() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        usuario.setActivo(false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));

        service.solicitar("ANA@example.com");

        verify(repository, never()).invalidarPendientes(anyLong(), any());
        verify(mailService, never()).sendHtml(any());
    }

    @Test
    void solicitarConCuentaSoloGoogleNoEnviaNada() {
        Usuario usuario = usuarioRecuperable(1L, null);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));

        service.solicitar("ana@example.com");

        verify(repository, never()).invalidarPendientes(anyLong(), any());
        verify(mailService, never()).sendHtml(any());
    }

    @Test
    void solicitarConCuentaRecuperableInvalidaAnterioresYEnviaCorreo() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(tokenAleatorioService.generar()).thenReturn("token-visible");
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");

        service.solicitar("ana@example.com");

        verify(repository).invalidarPendientes(eq(1L), eq(Instant.parse("2026-09-09T12:00:00Z")));
        verify(mailService).sendHtml(any());
        verify(repository).saveAndFlush(any(TokenRecuperacionAcceso.class));
    }

    @Test
    void solicitarConFalloDeEnvioNoLanzaExcepcionYMarcaError() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(tokenAleatorioService.generar()).thenReturn("token-visible");
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");
        org.mockito.Mockito.doThrow(new MailDeliveryException()).when(mailService).sendHtml(any());

        service.solicitar("ana@example.com");

        verify(repository).saveAndFlush(any(TokenRecuperacionAcceso.class));
    }

    @Test
    void esValidoConTokenVigenteDevuelveTrue() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        TokenRecuperacionAcceso token = tokenVigente(10L, usuario);
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");
        when(repository.findByTokenHash("hash-token")).thenReturn(Optional.of(token));

        assertTrue(service.esValido("token-visible"));
    }

    @Test
    void esValidoConTokenInexistenteDevuelveFalse() {
        when(hashTokenService.sha256("token-random")).thenReturn("hash-random");
        when(repository.findByTokenHash("hash-random")).thenReturn(Optional.empty());

        assertFalse(service.esValido("token-random"));
    }

    @Test
    void esValidoConTokenVencidoDevuelveFalse() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        TokenRecuperacionAcceso token = tokenVigente(10L, usuario);
        token.setExpiraEn(Instant.parse("2026-09-09T11:59:59Z"));
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");
        when(repository.findByTokenHash("hash-token")).thenReturn(Optional.of(token));

        assertFalse(service.esValido("token-visible"));
    }

    @Test
    void esValidoConTokenYaUtilizadoDevuelveFalse() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        TokenRecuperacionAcceso token = tokenVigente(10L, usuario);
        token.setUtilizadoEn(Instant.parse("2026-09-09T11:30:00Z"));
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");
        when(repository.findByTokenHash("hash-token")).thenReturn(Optional.of(token));

        assertFalse(service.esValido("token-visible"));
    }

    @Test
    void cambiarConContrasenasDiferentesLanzaValidacionSinConsultarToken() {
        NuevaContrasenaRequest peticion = new NuevaContrasenaRequest("Clave123", "Otra123");

        assertThrows(BusinessValidationException.class, () -> service.cambiar("token", peticion));

        verify(repository, never()).bloquearPorHash(anyString());
    }

    @Test
    void cambiarConTokenInexistenteLanzaTokenInvalido() {
        NuevaContrasenaRequest peticion = new NuevaContrasenaRequest("Clave123", "Clave123");
        when(hashTokenService.sha256("token-random")).thenReturn("hash-random");
        when(repository.bloquearPorHash("hash-random")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> service.cambiar("token-random", peticion));
    }

    @Test
    void cambiarConTokenVencidoLanzaTokenInvalido() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        TokenRecuperacionAcceso token = tokenVigente(10L, usuario);
        token.setExpiraEn(Instant.parse("2026-09-09T11:59:59Z"));
        NuevaContrasenaRequest peticion = new NuevaContrasenaRequest("Clave123", "Clave123");
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");
        when(repository.bloquearPorHash("hash-token")).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> service.cambiar("token-visible", peticion));
    }

    @Test
    void cambiarConTokenYaUtilizadoLanzaTokenInvalido() {
        Usuario usuario = usuarioRecuperable(1L, "hash-real");
        TokenRecuperacionAcceso token = tokenVigente(10L, usuario);
        token.setUtilizadoEn(Instant.parse("2026-09-09T11:30:00Z"));
        NuevaContrasenaRequest peticion = new NuevaContrasenaRequest("Clave123", "Clave123");
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");
        when(repository.bloquearPorHash("hash-token")).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> service.cambiar("token-visible", peticion));
    }

    @Test
    void cambiarConTokenVigenteActualizaContrasenaYConsumeElToken() {
        Usuario usuario = usuarioRecuperable(1L, "hash-viejo");
        TokenRecuperacionAcceso token = tokenVigente(10L, usuario);
        NuevaContrasenaRequest peticion = new NuevaContrasenaRequest("Clave123", "Clave123");
        when(hashTokenService.sha256("token-visible")).thenReturn("hash-token");
        when(repository.bloquearPorHash("hash-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("Clave123")).thenReturn("hash-nuevo");

        service.cambiar("token-visible", peticion);

        assertEquals("hash-nuevo", usuario.getContrasenaHash());
        assertFalse(usuario.isRequiereCambioContrasena());
        assertNotNull(token.getUtilizadoEn());
        verify(repository).invalidarOtrosPendientes(eq(1L), eq(10L), eq(Instant.parse("2026-09-09T12:00:00Z")));
    }
}
