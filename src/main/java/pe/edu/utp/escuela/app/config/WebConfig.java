package pe.edu.utp.escuela.app.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
        for (String carpeta : new String[] {"cursos", "firmas", "entidades", "docentes", "portadas"}) {
            registry.addResourceHandler("/uploads/" + carpeta + "/**").addResourceLocations(base + carpeta + "/");
        }
    }
}
