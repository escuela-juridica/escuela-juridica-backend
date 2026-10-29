package pe.edu.utp.escuela.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SecurityComponentsTests {

    private JwtService jwtService;
    private SessionCookieService sessionCookieService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(
                "esejur-clave-entornos-no-productivos-2026-para-pruebas".getBytes(), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("esejur-api"));

        Clock clock = Clock.fixed(Instant.now(), ZoneId.of("America/Lima"));

        jwtService = new JwtService(jwtEncoder, jwtDecoder, clock, "esejur-api", 60L);
        sessionCookieService = new SessionCookieService("ESEJUR_SESION", false, "Lax", 60L);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Test
    void jwtAndSessionCookieShareTheConfiguredSession() {
        String token = jwtService.issue(10L, "alumno@esejur.pe", List.of("ALUMNO"));
        Jwt decoded = jwtService.decode(token);
        ResponseCookie cookie = sessionCookieService.create(token);

        assertEquals("10", decoded.getSubject());
        assertEquals("alumno@esejur.pe", decoded.getClaimAsString("email"));
        assertTrue(decoded.getClaimAsStringList("roles").contains("ALUMNO"));
        assertEquals("ESEJUR_SESION", cookie.getName());
        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
    }

    @Test
    void passwordEncoderCreatesAndReadsPrefixedBcryptHashes() {
        String hash = passwordEncoder.encode("Marco1415@");

        assertTrue(hash.startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("Marco1415@", hash));
    }
}
