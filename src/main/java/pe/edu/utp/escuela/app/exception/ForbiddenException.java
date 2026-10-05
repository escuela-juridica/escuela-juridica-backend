package pe.edu.utp.escuela.app.exception;

import org.springframework.http.HttpStatus;

/** Sesión válida pero sin el rol requerido. */
public class ForbiddenException extends BusinessException {
    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para realizar esta operación");
    }
}
