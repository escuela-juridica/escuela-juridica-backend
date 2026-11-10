package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.time.LocalDate;

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
