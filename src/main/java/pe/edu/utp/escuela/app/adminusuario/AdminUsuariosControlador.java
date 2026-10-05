package pe.edu.utp.escuela.app.adminusuario;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.ActualizarPerfilPeticion;
import pe.edu.utp.escuela.app.dto.PageResponse;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@Tag(name = "HU-008 Gestionar usuarios", description = "Búsqueda, alta, roles y habilitación de cuentas, para administradores")
public class AdminUsuariosControlador {

    private final AdminUsuariosServicio servicio;

    @GetMapping
    @Operation(summary = "Listar usuarios (búsqueda y filtros opcionales, paginado)")
    @ApiResponse(responseCode = "200", description = "Página de usuarios")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<PageResponse<UsuarioAdminRespuesta>> listar(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) RolUsuarioAdmin rol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.listar(texto, activo, rol, PageRequest.of(Math.max(page, 0), tamano)));
    }

    @GetMapping("/{usuarioId}")
    @Operation(summary = "Consultar el detalle administrativo de un usuario")
    @ApiResponse(responseCode = "200", description = "Detalle del usuario")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<UsuarioAdminRespuesta> obtener(@PathVariable Long usuarioId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.obtener(usuarioId));
    }

    @PutMapping("/{usuarioId}/datos-personales")
    @Operation(summary = "Actualizar los datos personales de una cuenta")
    @ApiResponse(responseCode = "200", description = "Datos actualizados")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    @ApiResponse(responseCode = "409", description = "El documento de identidad ya está en uso")
    public ResponseEntity<UsuarioAdminRespuesta> actualizarDatosPersonales(
            @PathVariable Long usuarioId, @Valid @RequestBody ActualizarPerfilPeticion p) {
        return ResponseEntity.ok(servicio.actualizarDatosPersonales(usuarioId, p));
    }

    @PostMapping
    @Operation(summary = "Crear un usuario administrativamente",
            description = "Si el correo ya existe, conserva la cuenta y solo concede el rol faltante; "
                    + "no cambia la contraseña ni duplica la identidad.")
    @ApiResponse(responseCode = "200", description = "Cuenta creada o rol concedido sobre una existente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "409", description = "El documento de identidad ya está en uso")
    public ResponseEntity<CrearUsuarioAdminRespuesta> crear(@Valid @RequestBody CrearUsuarioAdminPeticion p) {
        return ResponseEntity.ok(servicio.crear(p));
    }

    @PostMapping("/{usuarioId}/roles")
    @Operation(summary = "Conceder un rol adicional a una cuenta existente",
            description = "No duplica la asignación si ya lo tiene; nunca cambia el rol principal.")
    @ApiResponse(responseCode = "200", description = "Rol concedido (o ya existente, sin cambios)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<UsuarioAdminRespuesta> concederRol(
            @PathVariable Long usuarioId, @Valid @RequestBody ConcederRolPeticion p) {
        return ResponseEntity.ok(servicio.concederRol(usuarioId, p));
    }

    @DeleteMapping("/{usuarioId}/roles/{rol}")
    @Operation(summary = "Retirar un rol de una cuenta",
            description = "Desviación deliberada de HU-008 (que dice que no se elimina roles), "
                    + "pedida explícitamente. Nunca deja una cuenta sin ningún rol.")
    @ApiResponse(responseCode = "200", description = "Rol retirado (o ya no lo tenía, sin cambios)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    @ApiResponse(responseCode = "409", description = "No puedes dejar la cuenta sin roles, ni retirarte tu propio rol de administrador, ni al último administrador")
    public ResponseEntity<UsuarioAdminRespuesta> revocarRol(
            @PathVariable Long usuarioId, @PathVariable RolUsuarioAdmin rol) {
        return ResponseEntity.ok(servicio.revocarRol(usuarioId, rol));
    }

    @PatchMapping("/{usuarioId}/activo")
    @Operation(summary = "Habilitar o deshabilitar una cuenta")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    @ApiResponse(responseCode = "409", description = "No puedes desactivar tu propia cuenta ni al último administrador")
    public ResponseEntity<UsuarioAdminRespuesta> cambiarActivo(
            @PathVariable Long usuarioId, @Valid @RequestBody CambiarActivoPeticion p) {
        return ResponseEntity.ok(servicio.cambiarActivo(usuarioId, p));
    }

    @PostMapping("/{usuarioId}/resetear-contrasena")
    @Operation(summary = "Resetear la contraseña de una cuenta",
            description = "Genera una contraseña temporal aleatoria nueva, fuerza el cambio en el "
                    + "próximo ingreso y la envía por correo. La respuesta la trae una sola vez.")
    @ApiResponse(responseCode = "200", description = "Contraseña restablecida")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<ResetearContrasenaRespuesta> resetearContrasena(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(servicio.resetearContrasena(usuarioId));
    }

    @PostMapping("/{usuarioId}/reenviar-habilitacion")
    @Operation(summary = "Reenviar el código de verificación vigente",
            description = "Solo reenvía si el correo todavía no está verificado; la contraseña "
                    + "temporal no se puede reenviar porque nunca se guarda en texto plano.")
    @ApiResponse(responseCode = "204", description = "Procesado (puede no haber reenviado nada si ya estaba verificado)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<Void> reenviarHabilitacion(@PathVariable Long usuarioId) {
        servicio.reenviarHabilitacion(usuarioId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
