package pe.edu.utp.escuela.app.exception;

import java.time.Clock;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.edu.utp.escuela.app.controller.RegistroController;
import pe.edu.utp.escuela.app.dto.ApiErrorResponse;
import pe.edu.utp.escuela.app.dto.FieldErrorResponse;

/** Añade errores de campos solo a HU-002, sin cambiar el manejador compartido. */
@RestControllerAdvice(assignableTypes = RegistroController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RegistroExceptionHandler {
    private final Clock clock;

    @ExceptionHandler(CampoRegistroException.class)
    ResponseEntity<ApiErrorResponse> campo(CampoRegistroException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus()).body(new ApiErrorResponse(
                clock.instant(), error.getStatus().value(), error.getCode(), error.getMessage(),
                request.getRequestURI(), List.of(new FieldErrorResponse(error.getCampo(), error.getMessage()))));
    }
}
