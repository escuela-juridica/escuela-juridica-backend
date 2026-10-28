package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.AccesoRequest;
import pe.edu.utp.escuela.app.dto.ResultadoAcceso;
import pe.edu.utp.escuela.app.dto.SesionAccesoResponse;
import pe.edu.utp.escuela.app.security.SessionCookieService;
import pe.edu.utp.escuela.app.service.AccesoService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "HU-001 Acceso",
        description = "Inicio de sesión con correo y contraseña, consulta y cierre de sesión")
public class AccesoController {

    private final AccesoService accesoService;
    private final SessionCookieService sessionCookieService;

    @PostMapping("/acceso")
    @Operation(summary = "Iniciar sesión con correo y contraseña",
            description = "Usa el mismo error para correo inexistente o contraseña incorrecta, sin revelar si "
                    + "el correo existe. El JWT nunca viaja en el cuerpo de la respuesta, solo en la cookie "
                    + "HttpOnly.")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada; cookie establecida")
    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos")
    @ApiResponse(responseCode = "403",
            description = "Cuenta deshabilitada o correo pendiente de verificar")
    public ResponseEntity<SesionAccesoResponse> acceder(@Valid @RequestBody AccesoRequest peticion) {
        ResultadoAcceso resultado = accesoService.acceder(peticion);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookieService.create(resultado.jwt()).toString())
                .body(resultado.sesion());
    }

    @GetMapping("/sesion")
    @Operation(summary = "Consultar la sesión actual",
            description = "Requiere la cookie de sesión vigente. Permite restaurar el estado de Angular al "
                    + "recargar la página.")
    @ApiResponse(responseCode = "200", description = "Datos mínimos del usuario autenticado")
    public SesionAccesoResponse sesionActual(Authentication autenticacion) {
        return accesoService.obtenerSesion(Long.valueOf(autenticacion.getName()));
    }

    @PostMapping("/cierre")
    @Operation(summary = "Cerrar sesión", description = "Invalida la cookie de sesión.")
    @ApiResponse(responseCode = "204", description = "Cookie eliminada")
    public ResponseEntity<Void> cerrar() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookieService.delete().toString())
                .build();
    }
}
