package pe.edu.utp.escuela.app.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import pe.edu.utp.escuela.app.dto.ApiErrorResponse;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {

    /** Rutas sin sesión, compartidas entre el {@code authorizeHttpRequests} de abajo y el
     * {@code sessionCookieBearerTokenResolver}: si no se comparten, una cookie de sesión vieja o
     * mal firmada (p. ej. tras rotar {@code security.jwt.secret}) tumba con 401 hasta las rutas
     * públicas, porque Spring intenta validar cualquier token presente ANTES de mirar si la ruta
     * lo exige. Por eso el resolver ignora la cookie por completo en estas rutas. */
    private static final String[] RUTAS_PUBLICAS = {
            "/api/health",
            "/api/publico/**",
            "/api/auth/acceso",
            "/api/auth/acceso/google",
            "/api/auth/registro/**",
            "/api/auth/verificacion/**",
            "/api/auth/recuperacion/**",
            "/api/testing/mail",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
    };

    @Bean
    @Primary
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret debe contener al menos 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    @Primary
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).build();
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            @Value("${security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Clave y JWT independientes de la sesión, exclusivos de la referencia de verificación de
     * correo (PF-005). Nunca deben compartir clave con la sesión: si la compartieran, esa
     * referencia (entregada apenas te registras, antes de confirmar nada) podría decodificarse
     * como si fuera una cookie de sesión válida.
     */
    @Bean
    @Qualifier("verificacionJwt")
    SecretKey verificacionJwtSecretKey(@Value("${security.jwt.verificacion-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("security.jwt.verificacion-secret debe contener al menos 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    @Qualifier("verificacionJwt")
    JwtEncoder verificacionJwtEncoder(@Qualifier("verificacionJwt") SecretKey verificacionJwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(verificacionJwtSecretKey).build();
    }

    @Bean
    @Qualifier("verificacionJwt")
    JwtDecoder verificacionJwtDecoder(
            @Qualifier("verificacionJwt") SecretKey verificacionJwtSecretKey,
            @Value("${security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(verificacionJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    BearerTokenResolver sessionCookieBearerTokenResolver(
            @Value("${security.cookie.name}") String cookieName) {
        AntPathMatcher matcher = new AntPathMatcher();
        return request -> {
            String ruta = request.getServletPath();
            boolean esPublica = Arrays.stream(RUTAS_PUBLICAS).anyMatch(patron -> matcher.match(patron, ruta));
            if (esPublica || request.getCookies() == null) {
                return null;
            }
            return Arrays.stream(request.getCookies())
                    .filter(cookie -> cookieName.equals(cookie.getName()))
                    .map(cookie -> cookie.getValue())
                    .findFirst()
                    .orElse(null);
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            BearerTokenResolver sessionCookieBearerTokenResolver,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JsonMapper jsonMapper,
            Clock clock) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(RUTAS_PUBLICAS)
                        .permitAll()
                        .requestMatchers("/api/auth/sesion", "/api/auth/cierre")
                        .authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(sessionCookieBearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(
                                response, jsonMapper, clock, request.getRequestURI(),
                                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Debes iniciar sesión")))
                .exceptionHandling(errors -> errors.accessDeniedHandler(
                        (request, response, exception) -> writeSecurityError(
                                response, jsonMapper, clock, request.getRequestURI(),
                                HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "No tienes permiso para realizar esta operación")))
                .logout(logout -> logout.disable());
        return http.build();
    }

    private void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            JsonMapper jsonMapper,
            Clock clock,
            String path,
            HttpStatus status,
            String code,
            String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                clock.instant(), status.value(), code, message, path, List.of()));
    }
}
