package pe.edu.utp.escuela.app.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {
    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para realizar esta operación");
    }
}
