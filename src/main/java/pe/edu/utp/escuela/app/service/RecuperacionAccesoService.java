package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import pe.edu.utp.escuela.app.dto.NuevaContrasenaPeticion;
import pe.edu.utp.escuela.app.entity.TokenRecuperacionAcceso;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.InvalidTokenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.TokenRecuperacionAccesoRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.HashTokenService;
import pe.edu.utp.escuela.app.security.TokenAleatorioService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@Service
public class RecuperacionAccesoService {

    private final UsuarioRepository usuarios;
    private final TokenRecuperacionAccesoRepository repository;
    private final TokenAleatorioService tokenAleatorioService;
    private final HashTokenService hashTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final MailService mailService;
    private final TextNormalizer textos;
    private final Clock clock;
    private final String frontendBaseUrl;

    public RecuperacionAccesoService(
            UsuarioRepository usuarios,
            TokenRecuperacionAccesoRepository repository,
            TokenAleatorioService tokenAleatorioService,
            HashTokenService hashTokenService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            MailService mailService,
            TextNormalizer textos,
            Clock clock,
            @Value("${application.frontend-base-url}") String frontendBaseUrl) {
        this.usuarios = usuarios;
        this.repository = repository;
        this.tokenAleatorioService = tokenAleatorioService;
        this.hashTokenService = hashTokenService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.mailService = mailService;
        this.textos = textos;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Nunca revela si el correo existe: el controlador responde igual pase lo que pase aquí. */
    @Transactional
    public void solicitar(String correoRecibido) {
        String correo = textos.normalizeEmail(correoRecibido);
        Optional<Usuario> encontrado = usuarios.findByCorreoIgnoreCase(correo);
        if (encontrado.isEmpty()) {
            return;
        }

        Usuario usuario = encontrado.get();
        if (!usuario.isActivo() || usuario.getContrasenaHash() == null) {
            return;
        }

        Instant ahora = clock.instant();
        repository.invalidarPendientes(usuario.getId(), ahora);
        String visible = tokenAleatorioService.generar();

        TokenRecuperacionAcceso token = new TokenRecuperacionAcceso();
        token.setUsuario(usuario);
        token.setTokenHash(hashTokenService.sha256(visible));
        token.setEstadoEnvio("PENDIENTE");
        token.setSolicitadoEn(ahora);
        token.setExpiraEn(ahora.plus(60, ChronoUnit.MINUTES));
        token.setModificadoEn(ahora);
        repository.saveAndFlush(token);

        String recoveryUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/nueva-password")
                .queryParam("token", visible)
                .build().encode().toUriString();
        try {
            mailService.sendHtml(HtmlMailMessage.to(
                    usuario.getCorreo(),
                    "Recupera tu acceso a ESEJUR",
                    "mail/password-recovery.html",
                    Map.of(
                            "name", usuario.getPersona().nombreCompleto(),
                            "recoveryUrl", recoveryUrl)));
            token.setEstadoEnvio("ENVIADO");
        } catch (MailDeliveryException exception) {
            token.setEstadoEnvio("ERROR");
        }
        token.setModificadoEn(clock.instant());
    }

    @Transactional(readOnly = true)
    public boolean esValido(String tokenVisible) {
        Optional<TokenRecuperacionAcceso> token =
                repository.findByTokenHash(hashTokenService.sha256(tokenVisible));
        return token.filter(this::vigente).isPresent();
    }

    @Transactional
    public void cambiar(String tokenVisible, NuevaContrasenaPeticion peticion) {
        if (!peticion.contrasena().equals(peticion.confirmacion())) {
            throw new BusinessValidationException("Las contraseñas no coinciden");
        }
        passwordPolicyService.validate(peticion.contrasena());

        TokenRecuperacionAcceso token = repository
                .bloquearPorHash(hashTokenService.sha256(tokenVisible))
                .orElseThrow(InvalidTokenException::new);
        if (!vigente(token)) {
            throw new InvalidTokenException();
        }

        Instant ahora = clock.instant();
        token.getUsuario().setContrasenaHash(passwordEncoder.encode(peticion.contrasena()));
        token.getUsuario().setRequiereCambioContrasena(false);
        token.setUtilizadoEn(ahora);
        token.setModificadoEn(ahora);
        repository.invalidarOtrosPendientes(token.getUsuario().getId(), token.getId(), ahora);
    }

    private boolean vigente(TokenRecuperacionAcceso token) {
        return token.getUtilizadoEn() == null
                && token.getInvalidadoEn() == null
                && token.getExpiraEn().isAfter(clock.instant());
    }
}
