package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/**
 * HU-047 — Cómo pagar una orden de billetera ya generada: imagen del QR, código de pago y página de instrucciones de
 * PagoEfectivo. Permite que el alumno que cerró el checkout vuelva a verlo mientras el pago siga pendiente.
 */
public record InstruccionesPagoResponse(String qrUrl, String enlace, String codigo, Instant venceEn) {
}
