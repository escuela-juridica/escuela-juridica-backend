package pe.edu.utp.escuela.app.dto;

public record ErrorValidacionCurso(
        String codigo,
        String seccion,
        String campo,
        String severidad,
        String mensaje) {
}
