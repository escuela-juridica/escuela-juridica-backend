package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import pe.edu.utp.escuela.app.dto.ContextoVerificacionResponse;
import pe.edu.utp.escuela.app.dto.ReenviarCodigoRequest;
import pe.edu.utp.escuela.app.dto.ReenvioCorreoResponse;
import pe.edu.utp.escuela.app.dto.VerificarCorreoRequest;
import pe.edu.utp.escuela.app.dto.VerificarCorreoResponse;
import pe.edu.utp.escuela.app.service.CodigoVerificacionService;

@RestController
@RequestMapping("/api/auth/verificacion")
@RequiredArgsConstructor
@Tag(name = "HU-003 Verificación de correo",
        description = "Confirma el código de seis dígitos de una cuenta pendiente creada en HU-002 o HU-008")
public class VerificacionCorreoController {

    private final CodigoVerificacionService service;

    @GetMapping("/{referencia}")
    @Operation(summary = "Resolver el contexto de una referencia de verificación",
            description = "Decodifica la referencia firmada que entrega HU-002 al registrarse y devuelve el "
                    + "correo de la cuenta. Permite reconstruir la pantalla PF-005 si se pierde el estado de "
                    + "navegación (recarga o enlace directo). La referencia no autoriza verificar el código.")
    @ApiResponse(responseCode = "200", description = "Referencia vigente; devuelve el correo de la cuenta")
    @ApiResponse(responseCode = "404", description = "Referencia inválida, vencida o cuenta inexistente")
    public ResponseEntity<ContextoVerificacionResponse> contexto(@PathVariable String referencia) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.obtenerContexto(referencia));
    }

    @PostMapping
    @Operation(summary = "Confirmar código de verificación",
            description = "Consume el código más reciente y vigente de la cuenta. Idempotente si el correo ya "
                    + "estaba verificado. Nunca revela si el correo existe o no.")
    @ApiResponse(responseCode = "200", description = "Correo verificado; indica si aún falta cambiar la contraseña")
    @ApiResponse(responseCode = "400", description = "Código inválido, vencido, usado o cuenta inexistente")
    public ResponseEntity<VerificarCorreoResponse> verificar(@Valid @RequestBody VerificarCorreoRequest p) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.verificar(p.correo(), p.codigo()));
    }

    @PostMapping("/reenvio")
    @Operation(summary = "Reenviar código de verificación",
            description = "Invalida cualquier código anterior y emite uno nuevo. No revela si el correo existe "
                    + "ni si la cuenta ya estaba verificada; solo informa si el envío fue aceptado.")
    @ApiResponse(responseCode = "202", description = "Solicitud procesada; enviado informa el resultado SMTP")
    @ApiResponse(responseCode = "400", description = "Correo con formato inválido")
    public ResponseEntity<ReenvioCorreoResponse> reenviar(@Valid @RequestBody ReenviarCodigoRequest p) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(new ReenvioCorreoResponse(service.reenviar(p.correo())));
    }
}
