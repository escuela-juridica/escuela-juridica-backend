package pe.edu.utp.escuela.app.exception;

import org.springframework.http.HttpStatus;

public class InvalidCurrentPasswordException extends BusinessException {
    public InvalidCurrentPasswordException() {
        super(HttpStatus.BAD_REQUEST, "CONTRASENA_ACTUAL_INCORRECTA", "La contraseña actual no es correcta");
    }
}
