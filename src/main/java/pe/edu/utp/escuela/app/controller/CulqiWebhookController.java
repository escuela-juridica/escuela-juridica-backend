package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.service.PagoEnLineaService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HU-047 — Webhook de Culqi. Es público (Culqi no tiene sesión) pero acepta un aviso solo si coincide la
 * autenticación básica configurada en CulqiPanel ("Activar autenticación"), y aun así el resultado se vuelve a
 * consultar a la API de Culqi antes de aplicarlo. Siempre responde 200 para evitar reintentos de un evento ya tratado.
 */
@Slf4j
@RestController
@RequestMapping("/api/publico/culqi")
@RequiredArgsConstructor
@Tag(name = "HU-047 Webhook de Culqi")
public class CulqiWebhookController {

    private final PagoEnLineaService servicio;
    private final JsonMapper json;

    @Value("${esejur.culqi.webhook-user:}")
    private String usuario;

    @Value("${esejur.culqi.webhook-password:}")
    private String clave;

    @PostMapping("/webhook")
    @Operation(summary = "Recibir un evento de Culqi (order.status.changed, charge.creation.succeeded, ...)")
    public ResponseEntity<Void> recibir(@RequestBody String cuerpo,
            @RequestHeader(value = "Authorization", required = false) String autorizacion) {
        if (!autenticado(autorizacion)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            JsonNode evento = json.readTree(cuerpo);
            JsonNode datos = evento.path("data");
            if (datos.isTextual()) {
                datos = json.readTree(datos.textValue()); // Culqi entrega "data" como texto JSON.
            }
            String id = evento.path("id").isTextual() ? evento.path("id").textValue() : null;
            String tipo = evento.path("type").isTextual() ? evento.path("type").textValue() : null;
            String recurso = datos.path("id").isTextual() ? datos.path("id").textValue() : null;
            servicio.procesarEventoCulqi(id, tipo, recurso);
        } catch (RuntimeException e) {
            log.warn("No se pudo procesar un evento de Culqi", e);
        }
        return ResponseEntity.ok().build();
    }

    private boolean autenticado(String autorizacion) {
        if (usuario.isBlank() && clave.isBlank()) {
            return true;
        }
        String esperado = "Basic " + Base64.getEncoder().encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
        return autorizacion != null && MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                autorizacion.getBytes(StandardCharsets.UTF_8));
    }
}
