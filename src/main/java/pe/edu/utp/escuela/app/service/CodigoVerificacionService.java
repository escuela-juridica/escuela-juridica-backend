package pe.edu.utp.escuela.app.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pe.edu.utp.escuela.app.dto.ContextoVerificacionResponse;
import pe.edu.utp.escuela.app.dto.VerificarCorreoResponse;
import pe.edu.utp.escuela.app.entity.*;
import pe.edu.utp.escuela.app.exception.InvalidCodeException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.*;
import pe.edu.utp.escuela.app.repository.CodigoVerificacionRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@Service @RequiredArgsConstructor
public class CodigoVerificacionService {
    private final CodigoVerificacionRepository codigos;
    private final UsuarioRepository usuarios;
    private final ReferenciaVerificacionService referencias;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final TextNormalizer textos;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Debe participar en la misma transacción que crea la cuenta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean emitirPara(Usuario usuario) {
        codigos.invalidarAnteriores(usuario.getId(), clock.instant());
        String visible = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        var codigo = new CodigoVerificacionCorreo();
        codigo.setUsuario(usuario);
        codigo.setCodigoHash(encoder.encode(visible));
        codigo.setSolicitadoEn(clock.instant());
        codigo.setModificadoEn(clock.instant());
        codigos.saveAndFlush(codigo);
        try {
            mail.sendHtml(new HtmlMailMessage(List.of(usuario.getCorreo()),
                    "Bienvenido a ESEJUR: verifica tu correo", "mail/verification-code.html",
                    Map.of("nombre", usuario.getPersona().getNombres(), "codigo", visible)));
            codigo.setEstadoEnvio("ENVIADO");
        } catch (MailDeliveryException exception) {
            codigo.setEstadoEnvio("ERROR");
            codigo.setInvalidadoEn(clock.instant());
        }
        codigo.setModificadoEn(clock.instant());
        codigos.saveAndFlush(codigo);
        return "ENVIADO".equals(codigo.getEstadoEnvio());
    }

    @Transactional
    public VerificarCorreoResponse verificar(String correoRecibido, String codigoVisible) {
        Usuario usuario = usuarios.findByCorreoIgnoreCase(textos.normalizeEmail(correoRecibido))
                .orElseThrow(InvalidCodeException::new);

        if (usuario.getCorreoVerificadoEn() != null) {
            return new VerificarCorreoResponse(usuario.isRequiereCambioContrasena());
        }

        CodigoVerificacionCorreo candidato = codigos
                .findByUsuario_IdAndUtilizadoEnIsNullAndInvalidadoEnIsNull(usuario.getId()).stream()
                .filter(c -> encoder.matches(codigoVisible, c.getCodigoHash()))
                .findFirst()
                .orElseThrow(InvalidCodeException::new);

        CodigoVerificacionCorreo codigo = codigos.findByIdParaActualizar(candidato.getId())
                .orElseThrow(InvalidCodeException::new);
        if (codigo.getUtilizadoEn() != null || codigo.getInvalidadoEn() != null) {
            throw new InvalidCodeException();
        }

        Instant ahora = clock.instant();
        codigo.setUtilizadoEn(ahora);
        codigo.setModificadoEn(ahora);
        codigo.getUsuario().setCorreoVerificadoEn(ahora);

        return new VerificarCorreoResponse(usuario.isRequiereCambioContrasena());
    }

    @Transactional(readOnly = true)
    public ContextoVerificacionResponse obtenerContexto(String referencia) {
        Long usuarioId = referencias.resolverUsuarioId(referencia);
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("La referencia no es válida o venció"));
        return new ContextoVerificacionResponse(usuario.getCorreo());
    }

    @Transactional
    public boolean reenviar(String correoRecibido) {
        return usuarios.findByCorreoIgnoreCase(textos.normalizeEmail(correoRecibido))
                .filter(usuario -> usuario.getCorreoVerificadoEn() == null)
                .map(this::emitirPara)
                .orElse(false);
    }
}
