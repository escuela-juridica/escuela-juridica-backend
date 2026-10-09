package pe.edu.utp.escuela.app.dto;

public record RecursoRespuesta(
        Long id,
        String tipo,
        String origen,
        String referencia,
        String nombreArchivo,
        String tipoMime,
        Long tamanoBytes,
        Integer duracionSegundos,
        boolean duracionDetectada,
        Boolean youtubeNoListadoConfirmado,
        String tipoMaterialCodigo,
        String tipoMaterialNombre) {
}
