package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;

class ReferenciaVerificacionServiceTests {

    private static final String ISSUER = "esejur-api";
    private SecretKey secretKey;
    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        secretKey = new SecretKeySpec(
                "esejur-clave-entornos-no-productivos-2026-para-pruebas".getBytes(), "HmacSHA256");
        jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        jwtDecoder = decoder;
    }

    private ReferenciaVerificacionService servicioConClock(Clock clock) {
        return new ReferenciaVerificacionService(jwtEncoder, jwtDecoder, clock, ISSUER);
    }

    @Test
    void emitirYResolverDevuelvenElMismoUsuarioId() {
        ReferenciaVerificacionService service =
                servicioConClock(Clock.fixed(Instant.now(), ZoneId.of("America/Lima")));

        String referencia = service.emitir(42L);

        assertEquals(42L, service.resolverUsuarioId(referencia));
    }

    @Test
    void resolverRechazaUnaReferenciaVencida() {
        Clock clockPasado = Clock.fixed(
                Instant.now().minus(Duration.ofMinutes(120)), ZoneId.of("America/Lima"));
        ReferenciaVerificacionService service = servicioConClock(clockPasado);
        String referencia = service.emitir(42L);

        ReferenciaVerificacionService servicioActual =
                servicioConClock(Clock.fixed(Instant.now(), ZoneId.of("America/Lima")));

        assertThrows(ResourceNotFoundException.class,
                () -> servicioActual.resolverUsuarioId(referencia));
    }

    @Test
    void resolverRechazaUnTokenSinElPropositoCorrecto() {
        Instant ahora = Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofMinutes(60)))
                .subject("42")
                .claim("proposito", "otro-proposito")
                .build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenConOtroProposito = jwtEncoder
                .encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        ReferenciaVerificacionService service =
                servicioConClock(Clock.fixed(ahora, ZoneId.of("America/Lima")));

        assertThrows(ResourceNotFoundException.class,
                () -> service.resolverUsuarioId(tokenConOtroProposito));
    }

    @Test
    void resolverRechazaUnaCadenaQueNoEsUnJwtValido() {
        ReferenciaVerificacionService service =
                servicioConClock(Clock.fixed(Instant.now(), ZoneId.of("America/Lima")));

        assertThrows(ResourceNotFoundException.class, () -> service.resolverUsuarioId("no-es-un-jwt"));
    }
}
