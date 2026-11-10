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
import pe.edu.utp.escuela.app.exception.InvalidTokenException;
import pe.edu.utp.escuela.app.integration.ContextoGoogleRegistroIntegration.Identidad;

@Service
public class ReferenciaGoogleService {

    private static final String CLAIM_PROPOSITO = "proposito";
    private static final String PROPOSITO_REGISTRO_GOOGLE = "registro-google";
    private static final Duration VIGENCIA = Duration.ofMinutes(15);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final String issuer;

    public ReferenciaGoogleService(
            @Qualifier("verificacionJwt") JwtEncoder jwtEncoder,
            @Qualifier("verificacionJwt") JwtDecoder jwtDecoder,
            Clock clock,
            @Value("${security.jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
        this.issuer = issuer;
    }

    public String emitir(Identidad identidad) {
        Instant ahora = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(VIGENCIA))
                .subject(identidad.subject())
                .claim(CLAIM_PROPOSITO, PROPOSITO_REGISTRO_GOOGLE)
                .claim("correo", identidad.correo());
        agregar(claims, "nombres", identidad.nombres());
        agregar(claims, "apellidos", identidad.apellidos());
        agregar(claims, "foto", identidad.fotoUrl());
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public Identidad resolver(String referencia) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(referencia);
        } catch (JwtException exception) {
            throw new InvalidTokenException();
        }
        String correo = jwt.getClaimAsString("correo");
        if (!PROPOSITO_REGISTRO_GOOGLE.equals(jwt.getClaimAsString(CLAIM_PROPOSITO))
                || jwt.getSubject() == null || correo == null) {
            throw new InvalidTokenException();
        }
        return new Identidad(jwt.getSubject(), correo, jwt.getClaimAsString("nombres"),
                jwt.getClaimAsString("apellidos"), jwt.getClaimAsString("foto"), jwt.getExpiresAt());
    }

    private void agregar(JwtClaimsSet.Builder claims, String nombre, String valor) {
        if (valor != null && !valor.isBlank()) {
            claims.claim(nombre, valor);
        }
    }
}
