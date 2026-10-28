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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.ActualizarPerfilPeticion;
import pe.edu.utp.escuela.app.dto.CambiarContrasenaPeticion;
import pe.edu.utp.escuela.app.dto.NuevaContrasenaPeticion;
import pe.edu.utp.escuela.app.dto.PerfilRespuesta;
import pe.edu.utp.escuela.app.service.PerfilService;

@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
@Tag(name = "HU-005 Mi perfil", description = "Consulta y actualización de los datos personales de la cuenta autenticada")
public class PerfilController {

    private final PerfilService service;

    @GetMapping
    @Operation(summary = "Consultar mi perfil")
    @ApiResponse(responseCode = "200", description = "Datos del perfil de la cuenta autenticada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public ResponseEntity<PerfilRespuesta> obtener() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.obtener());
    }

    @PutMapping
    @Operation(summary = "Actualizar mis datos personales")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "409", description = "El documento de identidad ya está en uso")
    public ResponseEntity<PerfilRespuesta> actualizar(@Valid @RequestBody ActualizarPerfilPeticion p) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.actualizar(p));
    }

    @PutMapping("/contrasena")
    @Operation(summary = "Crear contraseña para una cuenta de acceso con Google",
            description = "Permite a una cuenta creada por Google definir una contraseña propia por primera vez")
    @ApiResponse(responseCode = "204", description = "Contraseña creada")
    @ApiResponse(responseCode = "400", description = "La contraseña no cumple la política o no coincide con la confirmación")
    @ApiResponse(responseCode = "409", description = "La cuenta no puede crear una contraseña por este medio")
    public ResponseEntity<Void> crearContrasena(@Valid @RequestBody NuevaContrasenaPeticion p) {
        service.crearContrasena(p);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/contrasena/cambio")
    @Operation(summary = "Cambiar mi contraseña actual",
            description = "Para cuentas que ya tienen contraseña propia: exige la contraseña "
                    + "actual antes de reemplazarla por una nueva.")
    @ApiResponse(responseCode = "204", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta, nueva contraseña inválida o no coincide")
    @ApiResponse(responseCode = "409", description = "La cuenta todavía no tiene una contraseña propia")
    public ResponseEntity<Void> cambiarContrasena(@Valid @RequestBody CambiarContrasenaPeticion p) {
        service.cambiarContrasena(p);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
