package pe.edu.utp.escuela.app.dto;

public record OpcionResponse(
        Long id,
        String texto,
        boolean esCorrecta,
        int orden) {
}
