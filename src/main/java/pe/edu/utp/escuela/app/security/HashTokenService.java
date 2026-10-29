package pe.edu.utp.escuela.app.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Hash de propósito general para tokens aleatorios (no contraseñas): SHA-256 permite búsqueda
 * exacta por igualdad, algo que BCrypt no ofrece porque genera una sal distinta cada vez. */
@Component
public class HashTokenService {

    public String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}
