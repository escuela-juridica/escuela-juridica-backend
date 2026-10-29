package pe.edu.utp.escuela.app.config;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

@Slf4j
public final class ValidadorConfiguracionProduccion {

    private static final List<String> OBLIGATORIAS = List.of(
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.mail.password", "security.jwt.secret", "security.jwt.verificacion-secret",
            "application.frontend-base-url", "cors.allowed-origin");

    private ValidadorConfiguracionProduccion() {
    }

    public static void validar(ConfigurableEnvironment entorno) {
        if (!entorno.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> faltantes = new ArrayList<>();
        for (String propiedad : OBLIGATORIAS) {
            try {
                String valor = entorno.getProperty(propiedad);
                if (valor == null || valor.isBlank()) {
                    faltantes.add(propiedad);
                }
            } catch (IllegalArgumentException e) {
                faltantes.add(propiedad);
            }
        }
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("Faltan variables de entorno de producción para: " + faltantes
                    + ". Revisa la sección \"JAR de producción\" del README.");
        }
        String llave = entorno.getProperty("esejur.culqi.private-key", "");
        if (llave.startsWith("sk_test_")) {
            log.warn("Perfil prod con llaves de Culqi de integración (sk_test_): los cobros no serán reales. "
                    + "Define ESEJUR_CULQI_PUBLIC_KEY y ESEJUR_CULQI_PRIVATE_KEY con las llaves de producción.");
        }
    }
}
