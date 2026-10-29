package pe.edu.utp.escuela.app.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
public class ValidadorVariablesProduccion implements BeanFactoryPostProcessor, EnvironmentAware {

    private record Variable(String propiedad, String nombre) {
    }

    private static final List<Variable> OBLIGATORIAS = List.of(
            new Variable("spring.datasource.url", "DB_URL"),
            new Variable("spring.datasource.username", "DB_USER"),
            new Variable("spring.datasource.password", "DB_PASSWORD"),
            new Variable("spring.mail.password", "MAIL_PASSWORD"),
            new Variable("security.jwt.secret", "JWT_SECRET"),
            new Variable("security.jwt.verificacion-secret", "JWT_VERIFICATION_SECRET"),
            new Variable("application.frontend-base-url", "FRONTEND_BASE_URL"),
            new Variable("cors.allowed-origin", "CORS_ALLOWED_ORIGIN"),
            new Variable("esejur.almacenamiento.directorio", "FILES_DIR"),
            new Variable("security.csrf.cookie-domain", "CSRF_COOKIE_DOMAIN"),
            new Variable("esejur.culqi.public-key", "ESEJUR_CULQI_PUBLIC_KEY"),
            new Variable("esejur.culqi.private-key", "ESEJUR_CULQI_PRIVATE_KEY"),
            new Variable("esejur.culqi.webhook-user", "ESEJUR_CULQI_WEBHOOK_USER"),
            new Variable("esejur.culqi.webhook-password", "ESEJUR_CULQI_WEBHOOK_PASSWORD"));

    private static final int LONGITUD_MINIMA_SECRETO_JWT = 32;
    private static final int LONGITUD_MAXIMA_CLAVE_WEBHOOK = 20;

    private Environment entorno;

    @Override
    public void setEnvironment(Environment entorno) {
        this.entorno = entorno;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> problemas = new ArrayList<>();
        Map<String, String> valores = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        for (Variable v : OBLIGATORIAS) {
            nombres.put(v.propiedad(), v.nombre());
            String valor = leer(v.propiedad());
            if (valor == null || valor.isBlank()) {
                problemas.add(v.nombre() + " no está definida o está vacía");
            } else {
                valores.put(v.propiedad(), valor.strip());
            }
        }
        revisarFormato(valores, nombres, problemas);
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Configuración de producción inválida (perfil prod). Corrige /etc/esejurce/app.env:\n - "
                    + String.join("\n - ", problemas));
        }
        advertir(valores);
    }

    private String leer(String propiedad) {
        try {
            String valor = entorno.getProperty(propiedad);
            return valor != null && valor.contains("${") ? null : valor;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void revisarFormato(Map<String, String> v, Map<String, String> n, List<String> problemas) {
        String url = v.get("spring.datasource.url");
        if (url != null && !url.startsWith("jdbc:postgresql://")) {
            problemas.add(n.get("spring.datasource.url") + " debe empezar con jdbc:postgresql://");
        }
        String secreto = v.get("security.jwt.secret");
        String verificacion = v.get("security.jwt.verificacion-secret");
        for (String propiedad : List.of("security.jwt.secret", "security.jwt.verificacion-secret")) {
            String valor = v.get(propiedad);
            if (valor != null && valor.length() < LONGITUD_MINIMA_SECRETO_JWT) {
                problemas.add(n.get(propiedad) + " es demasiado corta (mínimo " + LONGITUD_MINIMA_SECRETO_JWT
                        + " caracteres; usa por ejemplo openssl rand -hex 48)");
            }
        }
        if (secreto != null && secreto.equals(verificacion)) {
            problemas.add("JWT_SECRET y JWT_VERIFICATION_SECRET deben ser distintos");
        }
        for (String propiedad : List.of("application.frontend-base-url", "cors.allowed-origin")) {
            String valor = v.get(propiedad);
            if (valor != null && !valor.startsWith("https://")) {
                problemas.add(n.get(propiedad) + " debe empezar con https://");
            }
            if (valor != null && valor.endsWith("/")) {
                problemas.add(n.get(propiedad) + " no debe terminar en /");
            }
        }
        String dominio = v.get("security.csrf.cookie-domain");
        if (dominio != null && (dominio.contains("/") || dominio.contains(":") || dominio.contains(" "))) {
            problemas.add(n.get("security.csrf.cookie-domain") + " debe ser solo el dominio, sin https://, puerto ni rutas");
        }
        String carpeta = v.get("esejur.almacenamiento.directorio");
        if (carpeta != null) {
            revisarCarpeta(carpeta, n.get("esejur.almacenamiento.directorio"), problemas);
        }
        String publica = v.get("esejur.culqi.public-key");
        String privada = v.get("esejur.culqi.private-key");
        if (publica != null && !publica.startsWith("pk_")) {
            problemas.add(n.get("esejur.culqi.public-key") + " debe empezar con pk_");
        }
        if (privada != null && !privada.startsWith("sk_")) {
            problemas.add(n.get("esejur.culqi.private-key") + " debe empezar con sk_");
        }
        if (publica != null && privada != null && publica.startsWith("pk_") && privada.startsWith("sk_")
                && publica.startsWith("pk_live_") != privada.startsWith("sk_live_")) {
            problemas.add("Las llaves de Culqi mezclan una de pruebas con una de producción");
        }
        if (entorno.getProperty("security.cookie.secure", "false").equals("false")) {
            problemas.add("security.cookie.secure debe ser true en producción");
        }
    }

    private void revisarCarpeta(String carpeta, String variable, List<String> problemas) {
        Path ruta;
        try {
            ruta = Path.of(carpeta);
        } catch (RuntimeException e) {
            problemas.add(variable + " no es una ruta válida");
            return;
        }
        if (!ruta.isAbsolute()) {
            problemas.add(variable + " debe ser una ruta absoluta (por ejemplo /opt/esejurce/files)");
            return;
        }
        Path existente = ruta;
        while (existente != null && !Files.exists(existente)) {
            existente = existente.getParent();
        }
        if (existente == null || !Files.isDirectory(existente) || !Files.isWritable(existente)) {
            problemas.add(variable + " apunta a una carpeta que no existe o en la que el servicio no puede escribir");
        }
    }

    private void advertir(Map<String, String> v) {
        if (v.get("esejur.culqi.private-key").startsWith("sk_test_") || v.get("esejur.culqi.public-key").startsWith("pk_test_")) {
            log.warn("Perfil prod con llaves de Culqi de integración (pk_test_/sk_test_): los cobros no serán reales.");
        }
        if (v.get("esejur.culqi.webhook-password").length() > LONGITUD_MAXIMA_CLAVE_WEBHOOK) {
            log.warn("La contraseña del webhook de Culqi supera {} caracteres: CulqiPanel no la aceptará.",
                    LONGITUD_MAXIMA_CLAVE_WEBHOOK);
        }
    }
}
