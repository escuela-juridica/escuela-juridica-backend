package pe.edu.utp.escuela.app.dto;

public record FirmanteCursoResponse(
        Long firmanteId,
        String nombreCompleto,
        String cargoFirma,
        int orden,
        boolean activo) {
}
