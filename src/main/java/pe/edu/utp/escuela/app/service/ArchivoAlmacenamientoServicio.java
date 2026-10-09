package pe.edu.utp.escuela.app.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.dto.ArchivoGuardado;
import pe.edu.utp.escuela.app.entity.ReglaArchivo;
import pe.edu.utp.escuela.app.entity.TipoMaterial;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.repository.ReglaArchivoRepositorio;

/** Guarda materiales subidos (origen SUBIDO) en disco local, validados contra las reglas de
 * HU-009. No es almacenamiento en la nube: alcanza para esta historia, pero una mudanza a un
 * bucket quedaría contenida en esta sola clase. */
@Service
public class ArchivoAlmacenamientoServicio {

    private final Path raiz;
    private final ReglaArchivoRepositorio reglasArchivo;

    public ArchivoAlmacenamientoServicio(
            @Value("${esejur.almacenamiento.directorio}") String directorio,
            ReglaArchivoRepositorio reglasArchivo) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
        this.reglasArchivo = reglasArchivo;
    }

    public ArchivoGuardado guardar(MultipartFile archivo, TipoMaterial tipoMaterial, Long cursoId) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessValidationException("Selecciona un archivo para subir.");
        }
        String nombreOriginal = StringUtils.cleanPath(
                archivo.getOriginalFilename() == null ? "archivo" : archivo.getOriginalFilename());
        String extension = extraerExtension(nombreOriginal);

        ReglaArchivo regla = reglasArchivo
                .findByTipoMaterial_IdAndExtensionIgnoreCaseAndActivoTrue(tipoMaterial.getId(), extension)
                .orElseThrow(() -> new BusinessValidationException(
                        extension.isEmpty()
                                ? "Ese archivo no tiene una extensión permitida para " + tipoMaterial.getNombre() + "."
                                : "La extensión ." + extension + " no está permitida para " + tipoMaterial.getNombre() + "."));
        if (archivo.getSize() > regla.getTamanoMaximoBytes()) {
            throw new BusinessValidationException(
                    "El archivo supera el tamaño máximo permitido (" + formatearTamano(regla.getTamanoMaximoBytes()) + ").");
        }

        try {
            Path carpetaCurso = raiz.resolve("cursos").resolve(String.valueOf(cursoId));
            Files.createDirectories(carpetaCurso);
            String nombreGuardado = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
            Path destino = carpetaCurso.resolve(nombreGuardado);
            archivo.transferTo(destino);
            String referencia = "/uploads/cursos/" + cursoId + "/" + nombreGuardado;
            return new ArchivoGuardado(referencia, nombreOriginal, archivo.getContentType(), archivo.getSize());
        } catch (IOException excepcion) {
            throw new BusinessValidationException("No pudimos guardar el archivo. Inténtalo nuevamente.");
        }
    }

    private String extraerExtension(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        if (punto < 0 || punto == nombreArchivo.length() - 1) {
            return "";
        }
        return nombreArchivo.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private String formatearTamano(long bytes) {
        if (bytes >= 1_073_741_824L) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / 1_073_741_824.0);
        }
        if (bytes >= 1_048_576L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
        }
        return (bytes / 1024) + " KB";
    }
}
