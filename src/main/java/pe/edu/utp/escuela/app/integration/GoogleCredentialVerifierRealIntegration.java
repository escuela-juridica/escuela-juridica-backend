package pe.edu.utp.escuela.app.integration;

import java.util.Collection;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.exception.BusinessException;

@Component
@ConditionalOnExpression("'${esejur.google.client-id:}' != ''")
public class GoogleCredentialVerifierRealIntegration implements GoogleCredentialVerifierIntegration {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> EMISORES = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;

    public GoogleCredentialVerifierRealIntegration(@Value("${esejur.google.client-id}") String clientId) {
        String esperado = clientId.strip();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> emisor = new JwtClaimValidator<Object>("iss",
                iss -> iss != null && EMISORES.contains(iss.toString()));
        OAuth2TokenValidator<Jwt> audiencia = new JwtClaimValidator<Object>("aud", aud -> {
            if (aud instanceof Collection<?> valores) {
                return valores.contains(esperado);
            }
            return esperado.equals(String.valueOf(aud));
        });
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), emisor, audiencia));
        this.decoder = nimbus;
    }

    @Override
    public ContextoGoogleRegistroIntegration.Identidad verificar(String credencial) {
        Jwt jwt;
        try {
            jwt = decoder.decode(credencial);
        } catch (BadJwtException exception) {
            throw invalida();
        } catch (JwtException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NO_DISPONIBLE",
                    "No pudimos comunicarnos con Google. Inténtalo nuevamente en unos minutos.");
        }
        String subject = jwt.getSubject();
        String correo = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || correo == null || correo.isBlank() || !correoVerificado(jwt)) {
            throw invalida();
        }
        return new ContextoGoogleRegistroIntegration.Identidad(subject, correo,
                jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("picture"), jwt.getExpiresAt());
    }

    private boolean correoVerificado(Jwt jwt) {
        Object valor = jwt.getClaim("email_verified");
        return valor instanceof Boolean verificado ? verificado : "true".equals(String.valueOf(valor));
    }

    private BusinessException invalida() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "GOOGLE_INVALIDO",
                "No pudimos validar tu cuenta de Google. Inténtalo nuevamente.");
    }
}
