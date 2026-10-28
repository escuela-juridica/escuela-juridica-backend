package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utp.escuela.app.dto.VerificarCorreoRespuesta;
import pe.edu.utp.escuela.app.entity.CodigoVerificacionCorreo;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.InvalidCodeException;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.CodigoVerificacionRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@ExtendWith(MockitoExtension.class)
class CodigoVerificacionServiceTests {

    @Mock private CodigoVerificacionRepository codigos;
    @Mock private UsuarioRepository usuarios;
    @Mock private ReferenciaVerificacionService referencias;
    @Mock private PasswordEncoder encoder;
    @Mock private MailService mail;

    private CodigoVerificacionService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneId.of("America/Lima"));
        service = new CodigoVerificacionService(
                codigos, usuarios, referencias, encoder, mail, new TextNormalizer(), clock);
    }

    private Usuario usuarioPendiente(Long id, boolean requiereCambio) {
        Persona persona = new Persona();
        persona.setNombres("Ana");
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPersona(persona);
        usuario.setCorreo("ana@example.com");
        usuario.setRequiereCambioContrasena(requiereCambio);
        return usuario;
    }

    private CodigoVerificacionCorreo codigoPendiente(Long id, Usuario usuario, String hash) {
        CodigoVerificacionCorreo codigo = new CodigoVerificacionCorreo();
        codigo.setId(id);
        codigo.setUsuario(usuario);
        codigo.setCodigoHash(hash);
        return codigo;
    }

    @Test
    void verificarConCodigoCorrectoMarcaUsadoYCorreoVerificado() {
        Usuario usuario = usuarioPendiente(1L, true);
        CodigoVerificacionCorreo candidato = codigoPendiente(10L, usuario, "hash-valido");
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(codigos.findByUsuario_IdAndUtilizadoEnIsNullAndInvalidadoEnIsNull(1L))
                .thenReturn(List.of(candidato));
        when(encoder.matches("123456", "hash-valido")).thenReturn(true);
        when(codigos.findByIdParaActualizar(10L)).thenReturn(Optional.of(candidato));

        VerificarCorreoRespuesta respuesta = service.verificar("ana@example.com", "123456");

        assertTrue(respuesta.requiereCambioContrasena());
        assertEquals(Instant.parse("2026-09-09T12:00:00Z"), candidato.getUtilizadoEn());
        assertEquals(Instant.parse("2026-09-09T12:00:00Z"), usuario.getCorreoVerificadoEn());
    }

    @Test
    void verificarCuentaYaVerificadaEsIdempotenteYNoConsultaCodigos() {
        Usuario usuario = usuarioPendiente(1L, false);
        usuario.setCorreoVerificadoEn(Instant.parse("2026-09-01T00:00:00Z"));
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));

        VerificarCorreoRespuesta respuesta = service.verificar("ana@example.com", "000000");

        assertFalse(respuesta.requiereCambioContrasena());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), usuario.getCorreoVerificadoEn());
        verify(codigos, never()).findByUsuario_IdAndUtilizadoEnIsNullAndInvalidadoEnIsNull(anyLong());
    }

    @Test
    void verificarCorreoInexistenteLanzaCodigoInvalidoSinRevelarLaCausa() {
        when(usuarios.findByCorreoIgnoreCase("nadie@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCodeException.class, () -> service.verificar("nadie@example.com", "123456"));
    }

    @Test
    void verificarCodigoQueNoCoincideConNingunoPendienteLanzaCodigoInvalido() {
        Usuario usuario = usuarioPendiente(1L, false);
        CodigoVerificacionCorreo otro = codigoPendiente(10L, usuario, "otro-hash");
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(codigos.findByUsuario_IdAndUtilizadoEnIsNullAndInvalidadoEnIsNull(1L))
                .thenReturn(List.of(otro));
        when(encoder.matches("111111", "otro-hash")).thenReturn(false);

        assertThrows(InvalidCodeException.class, () -> service.verificar("ana@example.com", "111111"));
    }

    @Test
    void verificarRechazaCuandoElBloqueoRevelaQueYaFueConsumidoPorOtraPeticion() {
        Usuario usuario = usuarioPendiente(1L, false);
        CodigoVerificacionCorreo candidato = codigoPendiente(10L, usuario, "hash-valido");
        CodigoVerificacionCorreo bloqueado = codigoPendiente(10L, usuario, "hash-valido");
        bloqueado.setUtilizadoEn(Instant.parse("2026-09-09T11:59:59Z"));
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(codigos.findByUsuario_IdAndUtilizadoEnIsNullAndInvalidadoEnIsNull(1L))
                .thenReturn(List.of(candidato));
        when(encoder.matches("123456", "hash-valido")).thenReturn(true);
        when(codigos.findByIdParaActualizar(10L)).thenReturn(Optional.of(bloqueado));

        assertThrows(InvalidCodeException.class, () -> service.verificar("ana@example.com", "123456"));
    }

    @Test
    void obtenerContextoDevuelveElCorreoDeLaReferenciaResuelta() {
        Usuario usuario = usuarioPendiente(7L, false);
        when(referencias.resolverUsuarioId("token-valido")).thenReturn(7L);
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));

        var contexto = service.obtenerContexto("token-valido");

        assertEquals("ana@example.com", contexto.correo());
    }

    @Test
    void obtenerContextoLanzaNoEncontradoCuandoElUsuarioYaNoExiste() {
        when(referencias.resolverUsuarioId("token-huerfano")).thenReturn(99L);
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThrows(pe.edu.utp.escuela.app.exception.ResourceNotFoundException.class,
                () -> service.obtenerContexto("token-huerfano"));
    }

    @Test
    void reenviarCorreoInexistenteNoEnviaNadaYDevuelveFalso() {
        when(usuarios.findByCorreoIgnoreCase("nadie@example.com")).thenReturn(Optional.empty());

        boolean enviado = service.reenviar("nadie@example.com");

        assertFalse(enviado);
        verify(mail, never()).sendHtml(any());
    }

    @Test
    void reenviarCuentaYaVerificadaNoEnviaNadaYDevuelveFalso() {
        Usuario usuario = usuarioPendiente(1L, false);
        usuario.setCorreoVerificadoEn(Instant.parse("2026-09-01T00:00:00Z"));
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));

        boolean enviado = service.reenviar("ana@example.com");

        assertFalse(enviado);
        verify(mail, never()).sendHtml(any());
        verify(codigos, never()).invalidarAnteriores(anyLong(), any());
    }

    @Test
    void reenviarCuentaPendienteInvalidaAnterioresYEnviaUnoNuevo() {
        Usuario usuario = usuarioPendiente(1L, false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario));
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hash-nuevo");

        boolean enviado = service.reenviar("ana@example.com");

        assertTrue(enviado);
        verify(codigos).invalidarAnteriores(eq(1L), eq(Instant.parse("2026-09-09T12:00:00Z")));
        verify(mail).sendHtml(any());
    }
}
