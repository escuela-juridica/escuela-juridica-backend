package pe.edu.utp.escuela.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HU-039 — El Libro de Reclamaciones recibe envíos sin sesión. El límite general de subida de la aplicación es alto
 * (videos de cursos), así que aquí se corta antes de leer el cuerpo cualquier envío que no pueda ser válido: hasta
 * 10 imágenes de 5 MB más el texto del formulario. Los archivos se siguen validando uno a uno en el servicio.
 */
@Component
public class LimiteCargaReclamacionFilter extends OncePerRequestFilter {

    static final long MAXIMO_BYTES = 10L * 5 * 1024 * 1024 + 512 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !(ruta.endsWith("/api/publico/reclamaciones") || ruta.endsWith("/api/app/reclamaciones"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAXIMO_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(("{\"status\":413,\"code\":\"PAYLOAD_TOO_LARGE\","
                    + "\"message\":\"El envío es demasiado grande: adjunta hasta 10 imágenes de 5 MB cada una.\"}")
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
