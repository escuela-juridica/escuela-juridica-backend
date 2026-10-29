package pe.edu.utp.escuela.app.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class TokenAleatorioService {
    private final SecureRandom random = new SecureRandom();

    public String generar() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
