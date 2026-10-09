package pe.edu.utp.escuela.app.dto;

public record ReglaArchivoRespuesta(
        Long id,
        Long tipoMaterialId,
        String tipoMaterialNombre,
        String extension,
        long tamanoMaximoBytes,
        boolean activo) {
}
