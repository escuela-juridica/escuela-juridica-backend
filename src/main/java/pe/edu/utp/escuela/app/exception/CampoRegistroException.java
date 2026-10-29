package pe.edu.utp.escuela.app.exception;

import org.springframework.http.HttpStatus;
public class CampoRegistroException extends BusinessException {
    private final String campo;
    public CampoRegistroException(HttpStatus status, String code, String campo, String mensaje) {
        super(status, code, mensaje);
        this.campo = campo;
    }
    public String getCampo() { return campo; }
}
