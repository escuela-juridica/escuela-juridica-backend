package pe.edu.utp.escuela.app.dto;
/** referenciaVerificacion identifica la cuenta; no es un token ni autoriza verificarla. */
public record RegistroResponse(Long usuarioId, String correo, boolean envioAceptado,
                                String referenciaVerificacion) {
    @Override public String toString() { return "RegistroResponse[referencia omitida]"; }
}
