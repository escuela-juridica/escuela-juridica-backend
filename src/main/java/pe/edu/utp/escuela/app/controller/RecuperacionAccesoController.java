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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.NuevaContrasenaRequest;
import pe.edu.utp.escuela.app.dto.RecuperacionSolicitadaResponse;
import pe.edu.utp.escuela.app.dto.SolicitarRecuperacionRequest;
import pe.edu.utp.escuela.app.dto.TokenRecuperacionResponse;
import pe.edu.utp.escuela.app.service.RecuperacionAccesoService;

@RestController
@RequestMapping("/api/auth/recuperacion")
@RequiredArgsConstructor
@Tag(name = "HU-004 Recuperación de acceso",
        description = "Permite definir una contraseña nueva mediante un enlace enviado por correo, "
                + "sin revelar públicamente si una cuenta existe")
public class RecuperacionAccesoController {

    private final RecuperacionAccesoService service;

    @PostMapping
    @Operation(summary = "Solicitar recuperación de acceso",
            description = "Siempre responde 202 con el mismo mensaje neutral, exista o no una "
                    + "cuenta con ese correo, y aunque falle el envío del correo.")
    @ApiResponse(responseCode = "202", description = "Solicitud procesada")
    public ResponseEntity<RecuperacionSolicitadaResponse> solicitar(
            @Valid @RequestBody SolicitarRecuperacionRequest peticion) {
        service.solicitar(peticion.correo());
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(RecuperacionSolicitadaResponse.neutral());
    }

    @GetMapping("/{token}")
    @Operation(summary = "Consultar vigencia del enlace",
            description = "Solo indica si el token todavía permite definir una contraseña nueva; "
                    + "no consume ni modifica nada.")
    @ApiResponse(responseCode = "200", description = "Indica si el token es válido")
    public ResponseEntity<TokenRecuperacionResponse> consultar(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TokenRecuperacionResponse(service.esValido(token)));
    }

    @PostMapping("/{token}")
    @Operation(summary = "Definir una nueva contraseña",
            description = "Consume el token si es válido y actualiza la contraseña de la cuenta.")
    @ApiResponse(responseCode = "204", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Token inválido, vencido, usado o contraseña inválida")
    public ResponseEntity<Void> cambiar(
            @PathVariable String token, @Valid @RequestBody NuevaContrasenaRequest peticion) {
        service.cambiar(token, peticion);
        return ResponseEntity.noContent().build();
    }
}
