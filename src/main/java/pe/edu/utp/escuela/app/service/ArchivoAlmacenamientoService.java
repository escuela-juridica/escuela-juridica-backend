package pe.edu.utp.escuela.app.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.dto.ArchivoGuardado;
import pe.edu.utp.escuela.app.entity.ReglaArchivo;
import pe.edu.utp.escuela.app.entity.TipoMaterial;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.ReglaArchivoRepository;

/** Guarda materiales subidos (origen SUBIDO) en disco local, validados contra las reglas de
 * HU-009. No es almacenamiento en la nube: alcanza para esta historia, pero una mudanza a un
 * bucket quedaría contenida en esta sola clase. */
@Service
public class ArchivoAlmacenamientoService {

    private static final long TAMANO_MAXIMO_IMAGEN_BYTES = 2L * 1024 * 1024;
    private static final long TAMANO_MAXIMO_ADJUNTO_BYTES = 5L * 1024 * 1024;

    private final Path raiz;
    private final ReglaArchivoRepository reglasArchivo;

    public ArchivoAlmacenamientoService(
            @Value("${esejur.almacenamiento.directorio}") String directorio,
            ReglaArchivoRepository reglasArchivo) {
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

    /** HU-023 — Resuelve una {@code referencia} ya guardada (forma
     * {@code "/uploads/cursos/{cursoId}/{archivo}"}) de vuelta a un recurso legible, para que un
     * servicio autenticado pueda transmitir sus bytes sin exponer la ruta real de almacenamiento.
     * Se defiende contra path traversal validando que el Path resuelto y normalizado siga
     * quedando dentro de {@code raiz} antes de devolver nada. */
    /**
     * HU-032 — Guarda un archivo generado por el propio sistema (p. ej. el PDF de un certificado) y
     * devuelve su referencia privada, la misma forma que usa {@link #cargar(String)}. El nombre lo
     * decide el servidor: nunca viene del usuario.
     */
    public String guardarBytes(String carpeta, String nombreArchivo, byte[] contenido) {
        Path destino = raiz.resolve(carpeta).resolve(nombreArchivo).normalize();
        if (!destino.startsWith(raiz)) {
            throw new BusinessValidationException("No pudimos guardar el archivo.");
        }
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (java.io.IOException e) {
            throw new BusinessValidationException("No pudimos guardar el archivo.");
        }
        return "/uploads/" + carpeta + "/" + nombreArchivo;
    }

    /**
     * HU-009 — Guarda la imagen de una firma o el logo de una entidad. Solo admite PNG o JPG, de hasta
     * 2 MB, y comprueba el contenido real del archivo (no solo su extensión). El nombre lo decide el
     * servidor y la referencia devuelta es la que se guarda en el firmante o la entidad.
     */
    public String guardarImagen(String carpeta, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessValidationException("Selecciona una imagen para subir.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_IMAGEN_BYTES) {
            throw new BusinessValidationException("La imagen supera el tamaño máximo permitido (2.0 MB).");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new BusinessValidationException("No pudimos leer la imagen. Inténtalo nuevamente.");
        }
        String extension = esPng(contenido) ? "png" : esJpeg(contenido) ? "jpg" : null;
        if (extension == null) {
            throw new BusinessValidationException("Solo se admiten imágenes PNG o JPG.");
        }
        return guardarBytes(carpeta, UUID.randomUUID() + "." + extension, contenido);
    }

    /** Valida una imagen adjunta a una reclamación (PNG, JPG o WEBP de hasta 5 MB) sin guardarla todavía. */
    public byte[] validarAdjuntoReclamacion(MultipartFile archivo) {
        String nombre = archivo == null || archivo.getOriginalFilename() == null ? "imagen" : archivo.getOriginalFilename();
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessValidationException("La imagen «" + nombre + "» está vacía.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_ADJUNTO_BYTES) {
            throw new BusinessValidationException("La imagen «" + nombre + "» supera el máximo de 5 MB.");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new BusinessValidationException("No pudimos leer la imagen «" + nombre + "».");
        }
        if (!esPng(contenido) && !esJpeg(contenido) && !esWebp(contenido)) {
            throw new BusinessValidationException("«" + nombre + "» no es una imagen válida (PNG, JPG o WEBP).");
        }
        return contenido;
    }

    /** Guarda un adjunto ya validado de la reclamación; el nombre lo decide el servidor. */
    public ArchivoGuardado guardarAdjuntoReclamacion(Long reclamacionId, MultipartFile archivo, byte[] contenido) {
        String extension = esPng(contenido) ? "png" : esJpeg(contenido) ? "jpg" : "webp";
        String tipoMime = switch (extension) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            default -> "image/webp";
        };
        String referencia = guardarBytes("reclamaciones/" + reclamacionId, UUID.randomUUID() + "." + extension, contenido);
        String original = StringUtils.cleanPath(archivo.getOriginalFilename() == null ? "imagen." + extension
                : archivo.getOriginalFilename());
        return new ArchivoGuardado(referencia, original, tipoMime, contenido.length);
    }

    private static boolean esWebp(byte[] b) {
        return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static boolean esPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean esJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    public Resource cargar(String referencia) {
        String prefijo = "/uploads/";
        if (referencia == null || !referencia.startsWith(prefijo)) {
            throw new BusinessValidationException("No pudimos ubicar el archivo solicitado.");
        }
        Path destino = raiz.resolve(referencia.substring(prefijo.length())).normalize();
        if (!destino.startsWith(raiz)) {
            throw new BusinessValidationException("No pudimos ubicar el archivo solicitado.");
        }
        if (!Files.isRegularFile(destino)) {
            throw new ResourceNotFoundException("El archivo solicitado no existe.");
        }
        try {
            Resource recurso = new UrlResource(destino.toUri());
            if (!recurso.isReadable()) {
                throw new ResourceNotFoundException("El archivo solicitado no existe.");
            }
            return recurso;
        } catch (MalformedURLException excepcion) {
            throw new ResourceNotFoundException("El archivo solicitado no existe.");
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
