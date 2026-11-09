package pe.edu.utp.escuela.app.service;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
