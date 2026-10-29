package pe.edu.utp.escuela.app.integration;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;

/**
 * HU-047 — Culqi simulado para desarrollo y demostración. Reproduce los resultados que Culqi comunica
 * (aprobado, rechazado, error, pendiente) a partir de un "token" de escenario que genera el checkout simulado
 * del navegador: {@code sim_tok_<ESCENARIO>}. No sustituye una prueba con las llaves de integración de Culqi.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "esejur.culqi.modo", havingValue = "SIMULADO", matchIfMissing = true)
public class CulqiSimuladoIntegration implements CulqiIntegration {

    private final Clock clock;

    @Override
    public boolean esSimulado() {
        return true;
    }

    @Override
    public String llavePublica() {
        return "pk_test_simulado";
    }

    @Override
    public CheckoutCulqi prepararCheckout(SolicitudCobro s) {
        return new CheckoutCulqi("SIMULADO", llavePublica(),"sim_ord_" + s.numeroPedido(), s.montoCentimos(),
                s.moneda(), "Escuela Jurídica");
    }

    @Override
    public ResultadoCulqi cobrar(CobroConToken c) {
        String token = c.tokenId() == null ? "" : c.tokenId();
        if (!token.startsWith("sim_tok_")) {
            throw new BusinessValidationException("El token de prueba no es válido.");
        }
        String cargo = "sim_chr_" + c.numeroPedido();
        String orden = "sim_ord_" + c.numeroPedido();
        return switch (token.substring("sim_tok_".length())) {
            case "APROBADO_TARJETA" -> resultado("APROBADO", cargo, orden, "TARJETA", "1111", 1, "venta_exitosa",
                    "Su compra ha sido exitosa.", c);
            case "APROBADO_YAPE" -> resultado("APROBADO", cargo, orden, "YAPE", null, null, "venta_exitosa",
                    "Su compra ha sido exitosa.", c);
            case "RECHAZADO_FONDOS" -> resultado("RECHAZADO", cargo, orden, "TARJETA", "0008", 1, "insufficient_funds",
                    "La tarjeta no tiene fondos suficientes.", c);
            case "RECHAZADO_ROBADA" -> resultado("RECHAZADO", cargo, orden, "TARJETA", "0000", 1, "stolen_card",
                    "La tarjeta fue reportada al banco emisor; usa otro medio de pago.", c);
            case "ERROR_PROCESO" -> resultado("ERROR", cargo, orden, "TARJETA", "0007", 1, "processing_error",
                    "La operación no pudo completarse. Inténtalo nuevamente.", c);
            case "PENDIENTE" -> resultado("PENDIENTE", null, orden, "BILLETERA", null, null, "pending",
                    "Culqi aún procesa tu pago.", c);
            default -> throw new BusinessValidationException("El escenario de prueba no existe.");
        };
    }

    @Override
    public ResultadoCulqi consultarOrden(String ordenId) {
        return null;
    }

    @Override
    public ResultadoCulqi consultarCargo(String cargoId) {
        return null;
    }

    private ResultadoCulqi resultado(String estado, String cargo, String orden, String medio, String digitos,
            Integer cuotas, String codigo, String mensaje, CobroConToken c) {
        return new ResultadoCulqi(estado, cargo, orden, medio, digitos, cuotas, codigo, mensaje, c.numeroPedido(),
                clock.instant());
    }
}
