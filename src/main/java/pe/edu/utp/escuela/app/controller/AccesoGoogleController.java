package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.AccesoGoogleRequest;
import pe.edu.utp.escuela.app.dto.AccesoGoogleResponse;
import pe.edu.utp.escuela.app.dto.ResultadoAccesoGoogle;
import pe.edu.utp.escuela.app.security.SessionCookieService;
import pe.edu.utp.escuela.app.service.AccesoGoogleService;

@RestController
@RequestMapping("/api/auth/acceso")
@RequiredArgsConstructor
@Tag(name = "HU-001 Acceso con Google",
        description = "Inicio de sesión con la credencial de Google Identity Services")
public class AccesoGoogleController {

    private final AccesoGoogleService servicio;
    private final SessionCookieService cookies;

    @PostMapping("/google")
    @Operation(summary = "Iniciar sesión con Google",
            description = "Valida la credencial de Google en el servidor. Si el correo ya tiene cuenta, la vincula "
                    + "cuando hace falta y abre sesión con la cookie HttpOnly (estado SESION). Si el correo es nuevo "
                    + "no crea nada: devuelve una referencia de corta duración para completar el registro "
                    + "(estado REGISTRO).")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada, o referencia para completar el registro")
    @ApiResponse(responseCode = "401", description = "Credencial de Google inválida")
    @ApiResponse(responseCode = "403", description = "Cuenta deshabilitada o correo pendiente de verificar")
    @ApiResponse(responseCode = "503", description = "Google no disponible o sin configurar")
    public ResponseEntity<AccesoGoogleResponse> acceder(@Valid @RequestBody AccesoGoogleRequest peticion) {
        ResultadoAccesoGoogle resultado = servicio.acceder(peticion.credencial());
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (resultado.jwt() != null) {
            respuesta.header(HttpHeaders.SET_COOKIE, cookies.create(resultado.jwt()).toString());
        }
        return respuesta.body(resultado.respuesta());
    }
}
