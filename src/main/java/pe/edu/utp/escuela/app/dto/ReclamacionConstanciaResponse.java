package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** HU-039 — Constancia que ve la persona al presentar su queja o reclamo: copia de lo presentado y el plazo. */
public record ReclamacionConstanciaResponse(
        String codigo,
        String tipo,
        String estado,
        Instant registradoEn,
        LocalDate fechaLimiteRespuesta,
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
        List<String> adjuntos,
        boolean constanciaEnviada) {
}
