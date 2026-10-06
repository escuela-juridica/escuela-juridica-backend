package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.exception.BusinessException;
import pe.edu.utp.escuela.app.exception.InvalidTokenException;

/** No incluye implementaciones simuladas ni permite omitir dependencias obligatorias. */
@Component
@RequiredArgsConstructor
public class IntegracionesRegistro {
    private final ObjectProvider<VerificadorAntiRobot> antiRobot;
    private final ObjectProvider<ContextoGoogleRegistro> google;
    private final Clock clock;

    public void verificarAntiRobot(String evidencia) {
        var verificador = antiRobot.getIfAvailable();
        if (verificador == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ANTIROBOT_PENDIENTE",
                    "El control anti-robot todavía no está integrado.");
        }
        verificador.verificar(evidencia);
    }

    public ContextoGoogleRegistro.Identidad identidadGoogle(String referencia) {
        if (referencia == null || referencia.isBlank()) throw new InvalidTokenException();
        var contexto = google.getIfAvailable();
        if (contexto == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_PENDIENTE",
                    "La referencia Google de HU-001 todavía no está integrada.");
        }
        var identidad = contexto.obtenerVerificada(referencia);
        if (identidad == null || identidad.subject() == null || identidad.subject().isBlank()
                || identidad.correo() == null || identidad.correo().isBlank()
                || identidad.venceEn() == null || !identidad.venceEn().isAfter(clock.instant())) {
            throw new InvalidTokenException();
        }
        return identidad;
    }
}
