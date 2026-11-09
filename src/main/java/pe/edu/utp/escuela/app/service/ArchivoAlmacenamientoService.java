package pe.edu.utp.escuela.app.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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

@Service
public class ArchivoAlmacenamientoService {

    private static final long TAMANO_MAXIMO_IMAGEN_BYTES = 2L * 1024 * 1024;
    private static final long TAMANO_MAXIMO_ADJUNTO_BYTES = 5L * 1024 * 1024;
    private static final int FIRMA_ANCHO_MINIMO_PX = 300;
    private static final int FIRMA_ANCHO_MAXIMO_PX = 3000;
    private static final int FIRMA_ALTO_MAXIMO_PX = 1500;
    private static final double FIRMA_PROPORCION_MINIMA = 2.0;
    private static final double FIRMA_PROPORCION_MAXIMA = 5.0;
    private static final long PORTADA_TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024;
    private static final int PORTADA_ANCHO_MINIMO_PX = 800;
    private static final int PORTADA_ANCHO_MAXIMO_PX = 6000;
    private static final double PORTADA_PROPORCION_MINIMA = 1.3;
    private static final double PORTADA_PROPORCION_MAXIMA = 2.4;

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

    public String guardarImagen(String carpeta, MultipartFile archivo) {
        return guardarImagen(carpeta, archivo, UUID.randomUUID().toString());
    }

    public String guardarImagen(String carpeta, MultipartFile archivo, String nombreSinExtension) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessValidationException("Selecciona una imagen para subir.");
        }
        boolean esPortada = "portadas".equals(carpeta);
        long tamanoMaximo = esPortada ? PORTADA_TAMANO_MAXIMO_BYTES : TAMANO_MAXIMO_IMAGEN_BYTES;
        if (archivo.getSize() > tamanoMaximo) {
            throw new BusinessValidationException(
                    "La imagen supera el tamaño máximo permitido (" + (esPortada ? "5.0" : "2.0") + " MB).");
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
        if ("firmas".equals(carpeta)) {
            validarFirma(contenido, extension);
        }
        if (esPortada) {
            validarPortada(contenido);
        }
        return guardarBytes(carpeta, nombreSinExtension + "." + extension, contenido);
    }

    public void retirarPortadasSinUso(Long cursoId, String... conservar) {
        Path carpeta = raiz.resolve("portadas");
        if (!Files.isDirectory(carpeta)) {
            return;
        }
        java.util.regex.Pattern patron = java.util.regex.Pattern.compile("^" + cursoId + "-\\d+\\.(jpg|png)$");
        java.util.Set<String> vigentes = new java.util.HashSet<>();
        for (String referencia : conservar) {
            if (referencia != null) {
                vigentes.add(referencia);
            }
        }
        try (var archivos = Files.list(carpeta)) {
            for (Path archivo : (Iterable<Path>) archivos::iterator) {
                String nombre = archivo.getFileName().toString();
                if (patron.matcher(nombre).matches() && !vigentes.contains("/uploads/portadas/" + nombre)) {
                    Files.deleteIfExists(archivo);
                }
            }
        } catch (IOException e) {
        }
    }

    private static void validarPortada(byte[] contenido) {
        int ancho;
        int alto;
        try (ImageInputStream flujo = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            var lectores = ImageIO.getImageReaders(flujo);
            if (!lectores.hasNext()) {
                throw new BusinessValidationException("No pudimos leer la portada. Prueba con otra imagen.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(flujo);
                ancho = lector.getWidth(0);
                alto = lector.getHeight(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new BusinessValidationException("No pudimos leer la portada. Prueba con otra imagen.");
        }
        double proporcion = alto == 0 ? 0 : (double) ancho / alto;
        if (ancho < PORTADA_ANCHO_MINIMO_PX || proporcion < PORTADA_PROPORCION_MINIMA || proporcion > PORTADA_PROPORCION_MAXIMA) {
            throw new BusinessValidationException(
                    "La portada debe ser horizontal (entre 1.3:1 y 2.4:1) y de al menos 800 px de ancho, por ejemplo 1200 x 675 px.");
        }
        if (ancho > PORTADA_ANCHO_MAXIMO_PX) {
            throw new BusinessValidationException("La portada es demasiado grande: usa como máximo 6000 px de ancho.");
        }
    }

    private static void validarFirma(byte[] contenido, String extension) {
        if (!"png".equals(extension)) {
            throw new BusinessValidationException("La firma debe ser un PNG con fondo transparente.");
        }
        int ancho;
        int alto;
        try (ImageInputStream flujo = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            var lectores = ImageIO.getImageReaders(flujo);
            if (!lectores.hasNext()) {
                throw new BusinessValidationException("No pudimos leer la imagen de la firma. Prueba con otro PNG.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(flujo);
                ancho = lector.getWidth(0);
                alto = lector.getHeight(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new BusinessValidationException("No pudimos leer la imagen de la firma. Prueba con otro PNG.");
        }
        double proporcion = alto == 0 ? 0 : (double) ancho / alto;
        if (ancho < FIRMA_ANCHO_MINIMO_PX || proporcion < FIRMA_PROPORCION_MINIMA || proporcion > FIRMA_PROPORCION_MAXIMA) {
            throw new BusinessValidationException(
                    "La firma debe ser horizontal (entre 2:1 y 5:1) y de al menos 300 px de ancho, por ejemplo 600 x 180 px.");
        }
        if (ancho > FIRMA_ANCHO_MAXIMO_PX || alto > FIRMA_ALTO_MAXIMO_PX) {
            throw new BusinessValidationException("La firma es demasiado grande: usa como máximo 3000 x 1500 px.");
        }
        BufferedImage imagen;
        try {
            imagen = ImageIO.read(new ByteArrayInputStream(contenido));
        } catch (IOException e) {
            imagen = null;
        }
        if (imagen == null || !tieneTransparencia(imagen)) {
            throw new BusinessValidationException("La firma debe tener fondo transparente. Exporta el PNG sin fondo.");
        }
    }

    private static boolean tieneTransparencia(BufferedImage imagen) {
        if (!imagen.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                if ((imagen.getRGB(x, y) >>> 24) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

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

    public ArchivoGuardado guardarComprobantePago(Long pagoId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessValidationException("Selecciona la boleta o captura que quieres adjuntar.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_ADJUNTO_BYTES) {
            throw new BusinessValidationException("El comprobante supera el máximo de 5 MB.");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new BusinessValidationException("No pudimos leer el comprobante.");
        }
        String extension = esPng(contenido) ? "png" : esJpeg(contenido) ? "jpg" : esWebp(contenido) ? "webp"
                : esPdf(contenido) ? "pdf" : null;
        if (extension == null) {
            throw new BusinessValidationException("El comprobante debe ser una imagen (PNG, JPG o WEBP) o un PDF.");
        }
        String referencia = guardarBytes("comprobantes/" + pagoId, UUID.randomUUID() + "." + extension, contenido);
        String original = StringUtils.cleanPath(archivo.getOriginalFilename() == null ? "comprobante." + extension
                : archivo.getOriginalFilename());
        return new ArchivoGuardado(referencia, original, tipoMimeDe(extension), contenido.length);
    }

    public static String tipoMimeDe(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private static boolean esPdf(byte[] b) {
        return b.length > 5 && b[0] == (byte) 0x25 && b[1] == (byte) 0x50 && b[2] == (byte) 0x44 && b[3] == (byte) 0x46;
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

    public void eliminar(String referencia) {
        String prefijo = "/uploads/";
        if (referencia == null || !referencia.startsWith(prefijo)) {
            return;
        }
        Path destino = raiz.resolve(referencia.substring(prefijo.length())).normalize();
        if (!destino.startsWith(raiz)) {
            return;
        }
        try {
            Files.deleteIfExists(destino);
        } catch (IOException e) {
            throw new BusinessValidationException("No pudimos retirar el archivo.");
        }
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
