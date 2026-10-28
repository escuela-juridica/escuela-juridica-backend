package pe.edu.utp.escuela.app.service;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * HU-032 — Obtiene los bytes de las imágenes que decoran el certificado (firmas y logo de la entidad).
 * Siempre salen del propio backend, nunca de otro servidor: la referencia que configura administración
 * es un archivo guardado ({@code /uploads/...}) o un recurso incluido en la aplicación, relativo a
 * {@code resources/certificado/} (p. ej. {@code firmas/lilia-guerra.png}). Si la imagen no está
 * disponible devuelve {@code null}: el certificado se emite igual, solo con texto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagenCertificadoService {

    private static final int TAMANO_MAXIMO_BYTES = 2 * 1024 * 1024;
    private static final String CARPETA_RECURSOS = "certificado/";

    private final ArchivoAlmacenamientoService archivos;

    public byte[] cargar(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return null;
        }
        String ref = referencia.trim();
        try {
            if (ref.startsWith("/uploads/")) {
                return limitar(archivos.cargar(ref).getContentAsByteArray());
            }
            String relativa = ref.replaceFirst("^/+", "");
            if (relativa.contains("..") || relativa.contains("\\") || relativa.contains(":")) {
                return null;
            }
            try (var entrada = new ClassPathResource(CARPETA_RECURSOS + relativa).getInputStream()) {
                return limitar(entrada.readNBytes(TAMANO_MAXIMO_BYTES + 1));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("No se pudo cargar la imagen del certificado {}", ref, e);
            return null;
        }
    }

    private static byte[] limitar(byte[] bytes) {
        return bytes.length <= TAMANO_MAXIMO_BYTES ? bytes : null;
    }
}
