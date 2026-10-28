package pe.edu.utp.escuela.app.dto;

public record ReglaArchivoResponse(
        Long id,
        Long tipoMaterialId,
        String tipoMaterialNombre,
        String extension,
        long tamanoMaximoBytes,
        boolean activo) {
}
