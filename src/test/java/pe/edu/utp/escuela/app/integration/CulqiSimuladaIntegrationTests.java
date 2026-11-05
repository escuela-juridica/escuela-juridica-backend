package pe.edu.utp.escuela.app.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.CobroConToken;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.SolicitudCobro;
import tools.jackson.databind.json.JsonMapper;

/** Contrato HTTP contra un servidor local que simula Culqi sin efectuar cobros reales. */
class CulqiSimuladaIntegrationTests {

    private HttpServer servidor;
    private CulqiRealIntegration culqi;
    private final AtomicReference<String> autorizacion = new AtomicReference<>();
    private final AtomicReference<String> cuerpoOrden = new AtomicReference<>();
    private final AtomicReference<String> cuerpoCargo = new AtomicReference<>();

    @BeforeEach
    void iniciarSimulador() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v2/orders", exchange -> responderOrden(exchange));
        servidor.createContext("/v2/charges", exchange -> responderCargo(exchange));
        servidor.start();

        String api = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v2";
        culqi = new CulqiRealIntegration(
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-11-04T16:00:00Z"), ZoneOffset.UTC),
                api,
                "pk_test_simulada",
                "sk_test_simulada",
                24);
    }

    @AfterEach
    void detenerSimulador() {
        servidor.stop(0);
    }

    @Test
    void creaOrdenYCobraTokenRespetandoElContratoHttpExterno() {
        var checkout = culqi.prepararCheckout(new SolicitudCobro(
                "PED-2026-000011", 32000, "PEN", "Curso de prueba", "ana@example.com",
                "Ana", "Pérez", "999999999"));

        assertEquals("pk_test_simulada", checkout.llavePublica());
        assertEquals("ord_test_001", checkout.ordenId());
        assertEquals("Bearer sk_test_simulada", autorizacion.get());
        assertTrue(cuerpoOrden.get().contains("\"amount\":32000"));
        assertTrue(cuerpoOrden.get().contains("\"currency_code\":\"PEN\""));
        assertTrue(cuerpoOrden.get().contains("PED-2026-000011-"));

        var resultado = culqi.cobrar(new CobroConToken(
                "tkn_test_abc", "PED-2026-000011", 32000, "PEN", "ana@example.com",
                "Curso de prueba", "Ana", "Pérez", "999999999"));

        assertEquals("APROBADO", resultado.estado());
        assertEquals("chr_test_001", resultado.cargoId());
        assertEquals("1111", resultado.ultimosDigitos());
        assertEquals("TARJETA", resultado.medio());
        assertTrue(cuerpoCargo.get().contains("\"source_id\":\"tkn_test_abc\""));
        assertTrue(cuerpoCargo.get().contains("\"numero_pedido\":\"PED-2026-000011\""));
    }

    private void responderOrden(HttpExchange exchange) throws IOException {
        autorizacion.set(exchange.getRequestHeaders().getFirst("Authorization"));
        cuerpoOrden.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        responder(exchange, 201, "{\"id\":\"ord_test_001\"}");
    }

    private void responderCargo(HttpExchange exchange) throws IOException {
        cuerpoCargo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        responder(exchange, 201, """
                {"id":"chr_test_001","order_id":"ord_test_001","installments":1,
                 "outcome":{"type":"venta_exitosa","user_message":"Compra simulada aprobada"},
                 "source":{"type":"card","card_number":"4111111111111111"}}
                """);
    }

    private static void responder(HttpExchange exchange, int estado, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(estado, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
