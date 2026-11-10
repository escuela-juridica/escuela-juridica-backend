package pe.edu.utp.escuela.app.integration;

import java.time.Instant;

public interface CulqiIntegration {

    String llavePublica();

    CheckoutCulqi prepararCheckout(SolicitudCobro solicitud);

    ResultadoCulqi cobrar(CobroConToken cobro);

    ResultadoCulqi consultarOrden(String ordenId);

    ResultadoCulqi consultarCargo(String cargoId);

    record SolicitudCobro(String numeroPedido, long montoCentimos, String moneda, String descripcion, String correo,
            String nombres, String apellidos, String telefono) {
    }

    record CheckoutCulqi(String llavePublica, String ordenId, long montoCentimos, String moneda, String titulo) {
    }

    record CobroConToken(String tokenId, String numeroPedido, long montoCentimos, String moneda, String correo,
            String descripcion, String nombres, String apellidos, String telefono) {
    }

    record ResultadoCulqi(String estado, String cargoId, String ordenId, String medio, String ultimosDigitos,
            Integer cuotas, String codigo, String mensajeUsuario, String numeroPedido, Instant fecha,
            InstruccionesPago instrucciones) {
    }

    record InstruccionesPago(String qrUrl, String enlace, String codigo, Instant venceEn) {
    }
}
