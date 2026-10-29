package pe.edu.utp.escuela.app.dto;

/** Solo circula dentro del backend: el jwt nunca debe salir en una respuesta HTTP. */
public record ResultadoAcceso(String jwt, SesionAccesoResponse sesion) {
}
