package pe.edu.utp.escuela.app.dto;

public record ArchivoGuardado(
        String referencia,
        String nombreOriginal,
        String tipoMime,
        long tamanoBytes) {
}
