package pe.edu.utp.escuela.app.service;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * HU-032 — Obtiene los bytes de las imágenes que decoran el certificado (firmas y logo de la entidad).
 * Las sube administración desde Información base y quedan en el almacenamiento ({@code /uploads/...}):
 * son datos, no parte de la aplicación. Si la imagen no está disponible devuelve {@code null} y el
 * certificado se emite igual, solo con texto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagenCertificadoService {

    private static final int TAMANO_MAXIMO_BYTES = 2 * 1024 * 1024;

    private final ArchivoAlmacenamientoService archivos;

    public byte[] cargar(String referencia) {
        if (referencia == null || !referencia.trim().startsWith("/uploads/")) {
            return null;
        }
        try {
            byte[] bytes = archivos.cargar(referencia.trim()).getContentAsByteArray();
            return bytes.length <= TAMANO_MAXIMO_BYTES ? bytes : null;
        } catch (IOException | RuntimeException e) {
            log.debug("No se pudo cargar la imagen del certificado {}", referencia, e);
            return null;
        }
    }
}
