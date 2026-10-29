package pe.edu.utp.escuela.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.edu.utp.escuela.app.dto.ApiErrorResponse;
import pe.edu.utp.escuela.app.dto.FieldErrorResponse;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> handleBusiness(
            BusinessException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(),
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleFields(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::mapFieldError)
                .toList();
        return response(HttpStatus.BAD_REQUEST, "INVALID_DATA",
                "Revisa los datos ingresados", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraints(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fields = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "INVALID_DATA",
                "Revisa los datos ingresados", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_BODY",
                "El contenido de la solicitud no es válido", request, List.of());
    }

    /** Una ruta o archivo estático que no existe (o que no se publica) responde 404, no un error interno. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNoResource(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no existe", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Restricción de datos incumplida en {}", request.getRequestURI());
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "La operación entra en conflicto con datos existentes", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "No pudimos completar la operación", request, List.of());
    }

    private FieldErrorResponse mapFieldError(FieldError error) {
        return new FieldErrorResponse(error.getField(),
                error.getDefaultMessage() == null ? "Valor no válido" : error.getDefaultMessage());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldErrorResponse> fields) {
        ApiErrorResponse body = new ApiErrorResponse(
                clock.instant(), status.value(), code, message, request.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
