package pe.edu.utp.escuela.app.maestra;

public record ReglaArchivoRespuesta(
        Long id,
        Long tipoMaterialId,
        String tipoMaterialNombre,
        String extension,
        long tamanoMaximoBytes,
        boolean activo) {
}
