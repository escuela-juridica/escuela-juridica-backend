package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReclamacionDetalleResponse(
        Long id,
        String codigo,
        String tipo,
        String estado,
        String plazo,
        int diasHabilesRestantes,
        LocalDate fechaLimite,
        Instant registradoEn,
        Instant respondidoEn,
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        String correo,
        String celular,
        String direccion,
        BigDecimal montoReclamado,
        String descripcionBien,
        String detalle,
        String pedido,
        boolean conCuenta,
        List<Adjunto> adjuntos,
        Respuesta respuesta,
        List<Intento> intentos) {

    public record Adjunto(Long id, String nombre, String tipoMime, long tamanoBytes) {
    }

    public record Respuesta(String texto, String respondidoPor, Instant creadoEn, Instant enviadoEn,
            String estadoEnvio, String correoDestino) {
    }

    public record Intento(Long id, String correoDestino, String resultado, String detalleError, String realizadoPor,
            Instant realizadoEn) {
    }
}
