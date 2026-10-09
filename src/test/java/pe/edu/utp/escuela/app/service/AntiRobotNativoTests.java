package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import pe.edu.utp.escuela.app.exception.BusinessException;

class AntiRobotNativoTests {
    static class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-09-05T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zona) { return this; }
        public Instant instant() { return ahora; }
    }
    Reloj reloj;
    AntiRobotNativo servicio;
    String visitante;
    @BeforeEach void preparar() {
        reloj = new Reloj(); servicio = new AntiRobotNativo(reloj); visitante = servicio.identificador();
    }
    private int respuesta(AntiRobotNativo.Desafio desafio) {
        var numeros = java.util.regex.Pattern.compile("(\\d+) \\+ (\\d+)").matcher(desafio.pregunta());
        assertTrue(numeros.find());
        return Integer.parseInt(numeros.group(1)) + Integer.parseInt(numeros.group(2));
    }
    private String evidencia() {
        var reto = servicio.crear(visitante, "local");
        return servicio.resolver(visitante, "local", reto.desafioId(), respuesta(reto)).evidencia();
    }
    @Test void aceptaUnaVezYRechazaInventada() {
        String token = evidencia();
        servicio.consumir(visitante, "local", token);
        assertThrows(BusinessException.class, () -> servicio.consumir(visitante, "local", token));
        assertThrows(BusinessException.class, () -> servicio.consumir(visitante, "local", "inventada"));
    }
    @Test void evidenciaNoPuedeTrasladarseAOtroVisitante() {
        String token = evidencia();
        assertThrows(BusinessException.class, () -> servicio.consumir(servicio.identificador(), "local", token));
        servicio.consumir(visitante, "local", token);
    }
    @Test void retoNoPuedeTrasladarseNiReutilizarse() {
        var reto = servicio.crear(visitante, "local");
        assertThrows(BusinessException.class, () -> servicio.resolver(servicio.identificador(), "local", reto.desafioId(), respuesta(reto)));
        servicio.resolver(visitante, "local", reto.desafioId(), respuesta(reto));
        assertThrows(BusinessException.class, () -> servicio.resolver(visitante, "local", reto.desafioId(), respuesta(reto)));
    }
    @Test void tresErroresAgotanReto() {
        var reto = servicio.crear(visitante, "local");
        for (int i = 0; i < 3; i++) assertThrows(BusinessException.class,
                () -> servicio.resolver(visitante, "local", reto.desafioId(), 99));
        assertThrows(BusinessException.class, () -> servicio.resolver(visitante, "local", reto.desafioId(), respuesta(reto)));
    }
    @Test void retoVenceEnDosMinutos() {
        var reto = servicio.crear(visitante, "local");
        reloj.ahora = reloj.ahora.plusSeconds(120);
        assertThrows(BusinessException.class, () -> servicio.resolver(visitante, "local", reto.desafioId(), respuesta(reto)));
    }
    @Test void evidenciaVenceEnDosMinutos() {
        String token = evidencia();
        reloj.ahora = reloj.ahora.plusSeconds(120);
        assertThrows(BusinessException.class, () -> servicio.consumir(visitante, "local", token));
    }
    @Test void refrescarInvalidaEvidenciaAnterior() {
        String token = evidencia();
        servicio.crear(visitante, "local");
        assertThrows(BusinessException.class, () -> servicio.consumir(visitante, "local", token));
    }
    @Test void limitePorVisitanteSeRecuperaTrasUnMinuto() {
        for (int i = 0; i < 10; i++) servicio.crear(visitante, "local");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> servicio.crear(visitante, "local")).getStatus());
        reloj.ahora = reloj.ahora.plusSeconds(60);
        assertNotNull(servicio.crear(visitante, "local"));
    }
    @Test void cambiarCookieNoEvitaLimitePorIp() {
        for (int i = 0; i < 30; i++) servicio.crear(servicio.identificador(), "local");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> servicio.crear(servicio.identificador(), "local")).getStatus());
    }
    @Test void consumoConcurrenteSoloTieneUnGanador() throws Exception {
        String token = evidencia();
        var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarea = () -> {
                inicio.await();
                try { servicio.consumir(visitante, "local", token); return true; }
                catch (BusinessException e) { return false; }
            };
            var a = pool.submit(tarea); var b = pool.submit(tarea); inicio.countDown();
            assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
        }
    }
}
