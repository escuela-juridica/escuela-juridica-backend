package pe.edu.utp.escuela.app.maestra;

public record FirmanteRespuesta(
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
