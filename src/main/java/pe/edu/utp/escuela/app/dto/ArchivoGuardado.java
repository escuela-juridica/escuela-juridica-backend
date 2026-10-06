package pe.edu.utp.escuela.app.dto;

/** {@code referencia}: ruta pública servida bajo {@code /uploads/...} (ver WebConfig). */
public record ArchivoGuardado(
        String referencia,
        String nombreOriginal,
        String tipoMime,
        long tamanoBytes) {
}
