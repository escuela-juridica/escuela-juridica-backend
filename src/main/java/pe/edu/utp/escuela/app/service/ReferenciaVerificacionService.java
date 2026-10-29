package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;

/**
 * Referencia opaca y firmada que identifica una cuenta pendiente en la URL de PF-005, sin exponer
 * un id secuencial adivinable ni el correo. No autoriza verificar el código; solo permite recuperar
 * el contexto (correo) cuando se pierde el estado de navegación, por ejemplo al recargar la página.
 */
@Service
public class ReferenciaVerificacionService {

    private static final String CLAIM_PROPOSITO = "proposito";
    private static final String PROPOSITO_VERIFICAR_CORREO = "verificar-correo";
    private static final Duration VIGENCIA = Duration.ofMinutes(60);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final String issuer;

    public ReferenciaVerificacionService(
            @Qualifier("verificacionJwt") JwtEncoder jwtEncoder,
            @Qualifier("verificacionJwt") JwtDecoder jwtDecoder,
            Clock clock,
            @Value("${security.jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
        this.issuer = issuer;
    }

    public String emitir(Long usuarioId) {
        Instant ahora = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(VIGENCIA))
                .subject(usuarioId.toString())
                .claim(CLAIM_PROPOSITO, PROPOSITO_VERIFICAR_CORREO)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Long resolverUsuarioId(String referencia) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(referencia);
        } catch (JwtException exception) {
            throw new ResourceNotFoundException("La referencia no es válida o venció");
        }
        if (!PROPOSITO_VERIFICAR_CORREO.equals(jwt.getClaimAsString(CLAIM_PROPOSITO))) {
            throw new ResourceNotFoundException("La referencia no es válida o venció");
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResourceNotFoundException("La referencia no es válida o venció");
        }
    }
}
