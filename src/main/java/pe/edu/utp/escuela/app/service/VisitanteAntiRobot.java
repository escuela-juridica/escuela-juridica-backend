package pe.edu.utp.escuela.app.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** Cookie de vinculación del desafío: no es una sesión ni concede autenticación. */
public final class VisitanteAntiRobot {
    private static final String NOMBRE = "HU002_ANTIROBOT";
    private VisitanteAntiRobot() {}

    public static String leer(HttpServletRequest request) {
        if (request != null && request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (NOMBRE.equals(cookie.getName()) && AntiRobotNativo.identificadorValido(cookie.getValue()))
                    return cookie.getValue();
            }
        }
        return null;
    }

    public static ResponseCookie crear(String visitante, boolean secure) {
        return ResponseCookie.from(NOMBRE, visitante).httpOnly(true).secure(secure)
                .sameSite("Strict").path("/api/auth/registro").maxAge(Duration.ofMinutes(30)).build();
    }
}
