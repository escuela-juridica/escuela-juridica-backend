package pe.edu.utp.escuela.app.integration;

import java.time.Instant;

/**
 * HU-047 — Puerto hacia Culqi. Culqi procesa el dinero y decide el resultado; ESEJUR solo inicia la operación
 * y registra lo que Culqi informa. La implementación es {@link CulqiRealIntegration} (API de Culqi); la llave
 * ({@code pk_test_}/{@code sk_test_} o {@code pk_live_}/{@code sk_live_}) decide si es integración o producción.
 */
public interface CulqiIntegration {

    /** Llave pública con la que el navegador abre el checkout (nunca la privada). */
    String llavePublica();

    /** Prepara lo que el navegador necesita para abrir el checkout de este intento. */
    CheckoutCulqi prepararCheckout(SolicitudCobro solicitud);

    /** Cobra el token (tarjeta o Yape) que el checkout entregó al navegador. */
    ResultadoCulqi cobrar(CobroConToken cobro);

    /** Estado actual de una orden (pagos diferidos o billeteras); {@code null} si Culqi no la reconoce. */
    ResultadoCulqi consultarOrden(String ordenId);

    /** Estado actual de un cargo; {@code null} si Culqi no lo reconoce. */
    ResultadoCulqi consultarCargo(String cargoId);

    record SolicitudCobro(String numeroPedido, long montoCentimos, String moneda, String descripcion, String correo,
            String nombres, String apellidos, String telefono) {
    }

    record CheckoutCulqi(String llavePublica, String ordenId, long montoCentimos, String moneda, String titulo) {
    }

    record CobroConToken(String tokenId, String numeroPedido, long montoCentimos, String moneda, String correo,
            String descripcion, String nombres, String apellidos, String telefono) {
    }

    /**
     * Resultado informado por Culqi. {@code estado} es PENDIENTE, APROBADO, RECHAZADO, ERROR o EXPIRADO.
     * {@code codigo} es el código de Culqi (por ejemplo {@code venta_exitosa} o {@code insufficient_funds}).
     */
    record ResultadoCulqi(String estado, String cargoId, String ordenId, String medio, String ultimosDigitos,
            Integer cuotas, String codigo, String mensajeUsuario, String numeroPedido, Instant fecha) {
    }
}
