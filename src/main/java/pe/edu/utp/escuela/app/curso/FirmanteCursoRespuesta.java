package pe.edu.utp.escuela.app.curso;

public record FirmanteCursoRespuesta(
        Long firmanteId,
        String nombreCompleto,
        String cargoFirma,
        int orden,
        boolean activo) {
}
