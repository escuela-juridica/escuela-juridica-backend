package pe.edu.utp.escuela.app.dto;
public record RegistroResponse(Long usuarioId, String correo, boolean envioAceptado,
                                String referenciaVerificacion) {
    @Override public String toString() { return "RegistroResponse[referencia omitida]"; }
}
