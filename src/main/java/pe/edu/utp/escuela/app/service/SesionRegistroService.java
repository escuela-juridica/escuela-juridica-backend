package pe.edu.utp.escuela.app.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.utp.escuela.app.dto.SesionRespuesta;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.security.JwtService;

/** Solo inicia la sesión del alumno recién creado, como exige HU-002. */
@Service
@RequiredArgsConstructor
public class SesionRegistroService {
    private final JwtService jwt;

    public SesionCreada crear(Usuario usuario) {
        var datos = new SesionRespuesta(usuario.getPersona().getNombres(), usuario.getCorreo(), "alumno");
        return new SesionCreada(datos, jwt.issue(usuario.getId(), usuario.getCorreo(), List.of("ALUMNO")));
    }

    public record SesionCreada(SesionRespuesta usuario, String token) {
        @Override public String toString() { return "SesionCreada[token omitido]"; }
    }
}
