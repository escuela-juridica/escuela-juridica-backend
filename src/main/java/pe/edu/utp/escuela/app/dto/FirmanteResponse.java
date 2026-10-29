package pe.edu.utp.escuela.app.dto;

public record FirmanteResponse(
        Long id,
        Long personaId,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String nombreCompleto,
        String cargoFirma,
        String imagenFirmaUrl,
        boolean activo) {
}
