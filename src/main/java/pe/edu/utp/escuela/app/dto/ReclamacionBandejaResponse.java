package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * HU-040 — Una fila de la bandeja de quejas y reclamos. {@code plazo} solo aplica a los pendientes: EN_PLAZO,
 * POR_VENCER (quedan tres días hábiles o menos) o VENCIDO. Un caso vencido sigue abierto: el vencimiento solo se
 * resalta. {@code estadoRespuesta}: SIN_RESPUESTA, PENDIENTE, ERROR (el último envío falló) o ENVIADO.
 */
public record ReclamacionBandejaResponse(
        Long id,
        String codigo,
        String tipo,
        String estado,
        String reclamante,
        String documento,
        String correo,
        Instant registradoEn,
        LocalDate fechaLimite,
        String plazo,
        int diasHabilesRestantes,
        long imagenes,
        String estadoRespuesta) {
}
