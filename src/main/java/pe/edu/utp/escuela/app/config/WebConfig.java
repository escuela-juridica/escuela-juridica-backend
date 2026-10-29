package pe.edu.utp.escuela.app.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** HU-011: sirve los materiales subidos (origen SUBIDO) como archivos estáticos bajo
 * {@code /uploads/cursos/**}. La protección real de materiales (que un visitante sin matrícula no
 * pueda abrir el enlace directo de un curso de pago) es trabajo de HU-022/HU-023, no de esta
 * historia; por ahora esa ruta es pública.
 *
 * <p>HU-009/HU-032: también sirve las imágenes de firmas y logos de entidades. Solo se publican
 * esas carpetas: {@code /uploads/certificados/} (los PDF) nunca se expone como archivo estático,
 * porque el certificado es privado y se entrega únicamente a su dueño desde su endpoint. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String directorioAlmacenamiento;

    public WebConfig(@Value("${esejur.almacenamiento.directorio}") String directorioAlmacenamiento) {
        this.directorioAlmacenamiento = directorioAlmacenamiento;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = Path.of(directorioAlmacenamiento).toAbsolutePath().normalize().toUri().toString();
        String base = ubicacion.endsWith("/") ? ubicacion : ubicacion + "/";
        for (String carpeta : new String[] {"cursos", "firmas", "entidades"}) {
            registry.addResourceHandler("/uploads/" + carpeta + "/**").addResourceLocations(base + carpeta + "/");
        }
    }
}
