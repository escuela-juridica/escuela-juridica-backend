package pe.edu.utp.escuela.app.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HU-047 — Cliente de la API de Culqi (https://api.culqi.com/v2). La misma URL sirve a integración y producción:
 * la llave ({@code sk_test_...} o {@code sk_live_...}) decide el entorno. La llave privada nunca sale del servidor.
 *
 * <p>Flujo: se crea una <b>orden</b> para el intento ({@code confirm=false}) y el navegador abre Culqi Checkout con
 * esa orden; la tarjeta y Yape entregan un token que aquí se cobra con {@code POST /charges}, y los medios
 * diferidos (billeteras) se confirman por webhook. Un webhook de Culqi no va firmado, así que siempre se vuelve a
 * consultar el recurso a la API antes de confiar en él.
 */
@Slf4j
@Service
public class CulqiRealIntegration implements CulqiIntegration {

    private final JsonMapper json;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String apiUrl;
    private final String llavePublica;
    private final String llavePrivada;
    private final long vigenciaOrdenHoras;

    public CulqiRealIntegration(JsonMapper json, Clock clock,
            @Value("${esejur.culqi.api-url:https://api.culqi.com/v2}") String apiUrl,
            @Value("${esejur.culqi.public-key:}") String llavePublica,
            @Value("${esejur.culqi.private-key:}") String llavePrivada,
            @Value("${esejur.culqi.order-validity-hours:24}") long vigenciaOrdenHoras) {
        if (llavePublica.isBlank() || llavePrivada.isBlank()) {
            throw new IllegalStateException(
                    "Culqi exige esejur.culqi.public-key y esejur.culqi.private-key (pk_test/sk_test en integración, pk_live/sk_live en producción).");
        }
        this.json = json;
        this.clock = clock;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.llavePublica = llavePublica;
        this.llavePrivada = llavePrivada;
        this.vigenciaOrdenHoras = vigenciaOrdenHoras;
    }

    @Override
    public String llavePublica() {
        return llavePublica;
    }

    @Override
    public CheckoutCulqi prepararCheckout(SolicitudCobro s) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("first_name", s.nombres());
        cliente.put("last_name", s.apellidos());
        cliente.put("email", s.correo());
        cliente.put("phone_number", s.telefono());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("amount", s.montoCentimos());
        cuerpo.put("currency_code", s.moneda());
        cuerpo.put("description", s.descripcion());
        // Culqi exige un order_number único en toda la cuenta: el sufijo evita choques si la numeración local reinicia.
        cuerpo.put("order_number", s.numeroPedido() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT));
        cuerpo.put("client_details", cliente);
        cuerpo.put("expiration_date", clock.instant().plus(Duration.ofHours(vigenciaOrdenHoras)).getEpochSecond());
        cuerpo.put("confirm", false);
        Respuesta r = enviar("POST", "/orders", cuerpo);
        String ordenId = texto(r.cuerpo(), "id");
        if (!r.exitosa() || ordenId == null) {
            log.warn("Culqi no creó la orden de {}: HTTP {} {}", s.numeroPedido(), r.estado(), r.cuerpo());
            throw new BusinessValidationException("No pudimos iniciar el pago con Culqi. Inténtalo nuevamente en unos minutos.");
        }
        return new CheckoutCulqi(llavePublica, ordenId, s.montoCentimos(), s.moneda(), "Escuela Jurídica");
    }

    @Override
    public ResultadoCulqi cobrar(CobroConToken c) {
        Map<String, Object> antifraude = new LinkedHashMap<>();
        antifraude.put("first_name", c.nombres());
        antifraude.put("last_name", c.apellidos());
        antifraude.put("phone_number", c.telefono());
        antifraude.put("country_code", "PE");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("amount", c.montoCentimos());
        cuerpo.put("currency_code", c.moneda());
        cuerpo.put("email", c.correo());
        cuerpo.put("source_id", c.tokenId());
        cuerpo.put("description", c.descripcion());
        cuerpo.put("capture", true);
        cuerpo.put("antifraud_details", antifraude);
        cuerpo.put("metadata", Map.of("numero_pedido", c.numeroPedido()));
        String medio = c.tokenId().startsWith("ype_") ? "YAPE" : "TARJETA";

        Respuesta r = enviar("POST", "/charges", cuerpo);
        if (r.estado() == 0) {
            // Sin respuesta no sabemos si el cobro se hizo: queda PENDIENTE hasta que Culqi lo comunique.
            return new ResultadoCulqi("PENDIENTE", null, null, medio, null, null, "sin_respuesta",
                    "Todavía no pudimos confirmar el resultado con Culqi.", c.numeroPedido(), clock.instant(), null);
        }
        JsonNode j = r.cuerpo();
        if (r.exitosa() && "venta_exitosa".equals(texto(j.path("outcome"), "type"))) {
            return new ResultadoCulqi("APROBADO", texto(j, "id"), texto(j, "order_id"), medio, ultimosDigitos(j),
                    entero(j, "installments"), "venta_exitosa", textoOr(j.path("outcome"), "user_message",
                            "Su compra ha sido exitosa."), c.numeroPedido(), clock.instant(), null);
        }
        // Rechazo del emisor (card_error) o error de la operación: Culqi lo informa como objeto "error".
        String tipoError = texto(j, "type");
        String codigoRechazo = texto(j, "decline_code");
        String estado = "card_error".equals(tipoError) || codigoRechazo != null ? "RECHAZADO" : "ERROR";
        String codigo = codigoRechazo != null ? codigoRechazo : texto(j, "code");
        return new ResultadoCulqi(estado, texto(j, "charge_id"), null, medio, null, null, codigo,
                textoOr(j, "user_message", "La operación no pudo completarse."), c.numeroPedido(), clock.instant(), null);
    }

    @Override
    public ResultadoCulqi consultarOrden(String ordenId) {
        Respuesta r = enviar("GET", "/orders/" + ordenId, null);
        if (!r.exitosa()) {
            return null;
        }
        JsonNode j = r.cuerpo();
        String estado = switch (textoOr(j, "state", "pending")) {
            case "paid" -> "APROBADO";
            case "expired" -> "EXPIRADO";
            case "deleted", "canceled" -> "ERROR";
            default -> "PENDIENTE";
        };
        return new ResultadoCulqi(estado, null, texto(j, "id"), "BILLETERA", null, null, textoOr(j, "state", "pending"),
                "Estado de la orden informado por Culqi.", texto(j, "order_number"), clock.instant(), instrucciones(j));
    }

    private static InstruccionesPago instrucciones(JsonNode orden) {
        String qr = urlDePagoEfectivo(texto(orden, "qr"));
        String enlace = urlDePagoEfectivo(texto(orden, "url_pe"));
        if (qr == null && enlace == null) {
            return null;
        }
        JsonNode vence = orden.path("expiration_date");
        return new InstruccionesPago(qr, enlace, texto(orden, "payment_code"),
                vence.isNumber() ? Instant.ofEpochSecond(vence.longValue()) : null);
    }

    /** Solo se entregan al alumno direcciones https de PagoEfectivo, nunca una URL cualquiera. */
    private static String urlDePagoEfectivo(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            boolean esPagoEfectivo = host != null && (host.equals("pagoefectivo.pe") || host.endsWith(".pagoefectivo.pe"));
            return "https".equals(uri.getScheme()) && esPagoEfectivo ? url : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public ResultadoCulqi consultarCargo(String cargoId) {
        Respuesta r = enviar("GET", "/charges/" + cargoId, null);
        if (!r.exitosa()) {
            return null;
        }
        JsonNode j = r.cuerpo();
        boolean exitosa = "venta_exitosa".equals(texto(j.path("outcome"), "type"));
        JsonNode metadata = j.path("metadata");
        return new ResultadoCulqi(exitosa ? "APROBADO" : "RECHAZADO", texto(j, "id"), texto(j, "order_id"),
                "ype".equals(texto(j.path("source"), "type")) ? "YAPE" : "TARJETA", ultimosDigitos(j),
                entero(j, "installments"), texto(j.path("outcome"), "type"),
                textoOr(j.path("outcome"), "user_message", ""), texto(metadata, "numero_pedido"), clock.instant(), null);
    }

    // ------------------------------------------------------------------------------------------ HTTP --

    private record Respuesta(int estado, JsonNode cuerpo) {
        boolean exitosa() {
            return estado >= 200 && estado < 300;
        }
    }

    private Respuesta enviar(String metodo, String ruta, Map<String, Object> cuerpo) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl + ruta)).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + llavePrivada).header("Content-Type", "application/json");
            if ("POST".equals(metodo)) {
                b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
            } else {
                b.GET();
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode nodo = r.body() == null || r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
            return new Respuesta(r.statusCode(), nodo);
        } catch (IOException | RuntimeException e) {
            log.warn("Sin respuesta de Culqi en {} {}", metodo, ruta, e);
            return new Respuesta(0, json.createObjectNode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Respuesta(0, json.createObjectNode());
        }
    }

    // --------------------------------------------------------------------------------------- lectura --

    private static String texto(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.isTextual() ? v.textValue() : v.toString();
    }

    private static String textoOr(JsonNode nodo, String campo, String porDefecto) {
        String v = texto(nodo, campo);
        return v == null || v.isBlank() ? porDefecto : v;
    }

    private static Integer entero(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        return v.isNumber() ? v.intValue() : null;
    }

    private static String ultimosDigitos(JsonNode cargo) {
        String numero = texto(cargo.path("source"), "card_number");
        return numero == null || numero.length() < 4 ? null : numero.substring(numero.length() - 4);
    }
}
