package pe.edu.utp.escuela.app.dto;

public record FirmanteCursoRespuesta(
        Long firmanteId,
        String nombreCompleto,
        String cargoFirma,
        int orden,
        boolean activo) {
}
