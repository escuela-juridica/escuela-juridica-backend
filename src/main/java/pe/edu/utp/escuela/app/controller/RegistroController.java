package pe.edu.utp.escuela.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.security.SessionCookieService;
import pe.edu.utp.escuela.app.service.RegistroService;

@RestController
@RequestMapping("/api/auth/registro")
@RequiredArgsConstructor
@Tag(name = "HU-002 Registro", description = "Creación de cuenta por formulario o contexto Google recibido de HU-001")
public class RegistroController {
    private final RegistroService registro;
    private final SessionCookieService cookies;

    @PostMapping
    @Operation(summary = "Crear cuenta con correo pendiente",
            description = "Exige aceptación legal y evidencia anti-robot validada por el servicio acordado con el equipo. "
                    + "La referencia de respuesta identifica la cuenta; no reemplaza el código que comprobará HU-003.")
    @ApiResponse(responseCode = "201", description = "Cuenta creada; envioAceptado informa el resultado SMTP")
    @ApiResponse(responseCode = "400", description = "Datos o evidencia inválidos")
    @ApiResponse(responseCode = "409", description = "Correo o documento duplicado")
    @ApiResponse(responseCode = "429", description = "Límite de comprobaciones alcanzado")
    @ApiResponse(responseCode = "503", description = "Comprobación anti-robot temporalmente no disponible")
    public ResponseEntity<RegistroRespuesta> registrar(@Valid @RequestBody RegistroPeticion p) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(registro.registrar(p));
    }

    @GetMapping("/google/{referencia}")
    @Operation(summary = "Consultar contexto Google recibido de HU-001",
            description = "No inicia OAuth. Requiere un adaptador que compruebe autenticidad de la referencia.")
    @ApiResponse(responseCode = "200", description = "Identidad autorizada y vigente")
    @ApiResponse(responseCode = "400", description = "Referencia inválida o vencida")
    @ApiResponse(responseCode = "409", description = "Cuenta existente")
    @ApiResponse(responseCode = "503", description = "Integración HU-001 pendiente")
    public ResponseEntity<ContextoRegistroGoogle> contexto(@PathVariable String referencia) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registro.contextoGoogle(referencia));
    }

    @PostMapping("/google")
    @Operation(summary = "Completar cuenta Google e iniciar sesión",
            description = "El correo y el identificador Google proceden del contexto validado, nunca del cuerpo del navegador.")
    @ApiResponse(responseCode = "201", description = "Cuenta verificada creada y cookie de sesión emitida")
    @ApiResponse(responseCode = "400", description = "Datos, aceptación o referencia inválidos")
    @ApiResponse(responseCode = "409", description = "Cuenta o documento existente")
    @ApiResponse(responseCode = "503", description = "Integración HU-001 pendiente")
    public ResponseEntity<SesionRespuesta> google(@Valid @RequestBody RegistroGooglePeticion p) {
        var sesion = registro.completarGoogle(p);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookies.create(sesion.token()).toString())
                .cacheControl(CacheControl.noStore()).body(sesion.usuario());
    }
}
