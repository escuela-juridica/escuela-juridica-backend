package pe.edu.utp.escuela.app.dto;

public record CondicionAvanceResponse(
        String codigo,
        String titulo,
        String valorActual,
        String meta,
        boolean cumplida) {
}
