package pe.edu.utp.escuela.app.dto;

public record OpcionRespuesta(
        Long id,
        String texto,
        boolean esCorrecta,
        int orden) {
}
