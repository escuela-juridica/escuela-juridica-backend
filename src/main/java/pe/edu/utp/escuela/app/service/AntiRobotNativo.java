package pe.edu.utp.escuela.app.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.edu.utp.escuela.app.exception.BusinessException;

/** Demostración académica, en memoria y para una sola instancia. No es un CAPTCHA robusto. */
@Service
public class AntiRobotNativo {
    private static final int CAPACIDAD = 10_000;
    private static final int VIGENCIA_SEGUNDOS = 120;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Reto> retos = new HashMap<>();
    private final Map<String, Prueba> pruebas = new HashMap<>();
    private final Map<String, Ventana> limites = new HashMap<>();

    public AntiRobotNativo(Clock clock) { this.clock = clock; }

    public record Desafio(String desafioId, String pregunta, int vigenciaSegundos) {}
    public record Evidencia(String evidencia, int vigenciaSegundos) {
        @Override public String toString() { return "Evidencia[omitida]"; }
    }
    private record Reto(String visitante, int respuesta, Instant vence, int intentos) {}
    private record Prueba(String visitante, Instant vence) {}
    private record Ventana(int cantidad, Instant vence) {}

    public String identificador() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean identificadorValido(String valor) {
        return valor != null && valor.matches("[A-Za-z0-9_-]{43}");
    }

    public synchronized Desafio crear(String visitante, String ip) {
        preparar("crear", visitante, ip, 10, 30);
        // Solo la comprobación más reciente de este navegador permanece vigente.
        retos.values().removeIf(r -> r.visitante().equals(visitante));
        pruebas.values().removeIf(p -> p.visitante().equals(visitante));
        comprobarCapacidad(retos);
        int a = random.nextInt(9) + 1, b = random.nextInt(9) + 1;
        String id = identificador();
        retos.put(id, new Reto(visitante, a + b, clock.instant().plusSeconds(VIGENCIA_SEGUNDOS), 0));
        return new Desafio(id, "¿Cuánto es " + a + " + " + b + "?", VIGENCIA_SEGUNDOS);
    }

    public synchronized Evidencia resolver(String visitante, String ip, String id, int respuesta) {
        preparar("resolver", visitante, ip, 20, 60);
        Reto reto = retos.get(id);
        if (reto == null || !reto.visitante().equals(visitante)) throw invalido();
        if (respuesta != reto.respuesta()) {
            int intentos = reto.intentos() + 1;
            if (intentos >= 3) retos.remove(id);
            else retos.put(id, new Reto(visitante, reto.respuesta(), reto.vence(), intentos));
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ANTIROBOT_RESPUESTA",
                    intentos >= 3 ? "Agotaste los tres intentos. Solicita otra comprobación."
                            : "La respuesta no es correcta. Intenta nuevamente.");
        }
        comprobarCapacidad(pruebas);
        retos.remove(id);
        String token = identificador();
        pruebas.put(token, new Prueba(visitante, clock.instant().plusSeconds(VIGENCIA_SEGUNDOS)));
        return new Evidencia(token, VIGENCIA_SEGUNDOS);
    }

    /** El bloqueo hace atómica la validación y el consumo, incluso con dos envíos simultáneos. */
    public synchronized void consumir(String visitante, String ip, String evidencia) {
        preparar("registro", visitante, ip, 10, 30);
        Prueba prueba = pruebas.get(evidencia);
        if (prueba == null || !prueba.visitante().equals(visitante)) throw invalido();
        pruebas.remove(evidencia);
    }

    private void preparar(String accion, String visitante, String ip, int porVisitante, int porIp) {
        Instant ahora = clock.instant();
        retos.values().removeIf(r -> !r.vence().isAfter(ahora));
        pruebas.values().removeIf(p -> !p.vence().isAfter(ahora));
        limites.values().removeIf(v -> !v.vence().isAfter(ahora));
        // La IP proviene del contenedor; no se confía en cabeceras enviadas por el cliente.
        limitar(accion + ":ip:" + ip, porIp, ahora);
        if (!identificadorValido(visitante)) throw invalido();
        limitar(accion + ":visitante:" + visitante, porVisitante, ahora);
    }

    private void limitar(String clave, int maximo, Instant ahora) {
        Ventana ventana = limites.get(clave);
        if (ventana == null) {
            comprobarCapacidad(limites);
            limites.put(clave, new Ventana(1, ahora.plusSeconds(60)));
        } else {
            if (ventana.cantidad() >= maximo) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "ANTIROBOT_LIMITE", "Demasiados intentos. Espera un minuto y vuelve a intentarlo.");
            limites.put(clave, new Ventana(ventana.cantidad() + 1, ventana.vence()));
        }
    }

    private void comprobarCapacidad(Map<?, ?> mapa) {
        if (mapa.size() >= CAPACIDAD) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "ANTIROBOT_OCUPADO", "La comprobación está ocupada. Inténtalo más tarde.");
    }

    public static BusinessException invalido() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "ANTIROBOT_INVALIDO",
                "La comprobación no es válida, venció o ya fue usada. Completa una nueva.");
    }
}
