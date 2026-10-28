package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.dto.ActivoRequest;
import pe.edu.utp.escuela.app.dto.ActualizarMaterialRequest;
import pe.edu.utp.escuela.app.dto.AsistenciaSesionResponse;
import pe.edu.utp.escuela.app.dto.ActualizarSesionRequest;
import pe.edu.utp.escuela.app.dto.CancelarSesionRequest;
import pe.edu.utp.escuela.app.dto.CorregirAsistenciaRequest;
import pe.edu.utp.escuela.app.dto.CorregirEnlaceSesionRequest;
import pe.edu.utp.escuela.app.dto.CrearLeccionRequest;
import pe.edu.utp.escuela.app.dto.CrearMaterialEnlaceRequest;
import pe.edu.utp.escuela.app.dto.CrearModuloRequest;
import pe.edu.utp.escuela.app.dto.HistorialSesionResponse;
import pe.edu.utp.escuela.app.dto.LeccionResponse;
import pe.edu.utp.escuela.app.dto.MaterialResponse;
import pe.edu.utp.escuela.app.dto.ModuloDisponibleResponse;
import pe.edu.utp.escuela.app.dto.ModuloResponse;
import pe.edu.utp.escuela.app.dto.OrdenRequest;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionRequest;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionResultado;
import pe.edu.utp.escuela.app.service.AdministrarSesionesService;
import pe.edu.utp.escuela.app.service.ContenidoService;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "HU-011 Contenido del curso", description = "Módulos, lecciones, materiales y sesiones en vivo (HU-012), para administradores")
public class ContenidoController {

    private final ContenidoService service;
    private final AdministrarSesionesService administrarSesionesService;

    @GetMapping("/cursos/{cursoId}/modulos")
    @Operation(summary = "Consultar la estructura completa de contenido de un curso")
    public ResponseEntity<List<ModuloResponse>> obtenerEstructura(@PathVariable Long cursoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.obtenerEstructura(cursoId));
    }

    @GetMapping("/modulos-disponibles")
    @Operation(summary = "Buscar módulos de cualquier curso para copiarlos",
            description = "Usado por \"agregar módulo existente\": la copia es completa e independiente.")
    public ResponseEntity<List<ModuloDisponibleResponse>> listarModulosDisponibles(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long excluirCursoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listarModulosDisponibles(texto, excluirCursoId));
    }

    @PostMapping("/cursos/{cursoId}/modulos")
    @Operation(summary = "Crear un módulo al final del curso")
    public ResponseEntity<ModuloResponse> crearModulo(
            @PathVariable Long cursoId, @Valid @RequestBody CrearModuloRequest p) {
        return ResponseEntity.ok(service.crearModulo(cursoId, p));
    }

    @PostMapping("/cursos/{cursoId}/copias-modulo/{moduloOrigenId}")
    @Operation(summary = "Copiar un módulo existente dentro de este curso",
            description = "Copia módulos, lecciones, materiales y exámenes de módulo; los materiales reutilizan el mismo recurso físico.")
    @ApiResponse(responseCode = "404", description = "El curso o el módulo de origen ya no existen")
    public ResponseEntity<ModuloResponse> copiarModulo(
            @PathVariable Long cursoId, @PathVariable Long moduloOrigenId) {
        return ResponseEntity.ok(service.copiarModulo(cursoId, moduloOrigenId));
    }

    @PutMapping("/cursos/{cursoId}/modulos/orden")
    @Operation(summary = "Reordenar los módulos de un curso")
    public ResponseEntity<List<ModuloResponse>> reordenarModulos(
            @PathVariable Long cursoId, @Valid @RequestBody OrdenRequest p) {
        return ResponseEntity.ok(service.reordenarModulos(cursoId, p));
    }

    @PutMapping("/modulos/{id}")
    @Operation(summary = "Editar título y descripción de un módulo")
    public ResponseEntity<ModuloResponse> actualizarModulo(
            @PathVariable Long id, @Valid @RequestBody CrearModuloRequest p) {
        return ResponseEntity.ok(service.actualizarModulo(id, p));
    }

    @PatchMapping("/modulos/{id}/activo")
    @Operation(summary = "Activar o desactivar un módulo (desactivar nunca borra)")
    public ResponseEntity<ModuloResponse> cambiarActivoModulo(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoModulo(id, p.activo()));
    }

    @DeleteMapping("/modulos/{id}")
    @Operation(summary = "Eliminar un módulo", description = "Borrado real, solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarModulo(@PathVariable Long id) {
        service.eliminarModulo(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/modulos/{moduloId}/lecciones")
    @Operation(summary = "Crear una lección al final del módulo")
    public ResponseEntity<LeccionResponse> crearLeccion(
            @PathVariable Long moduloId, @Valid @RequestBody CrearLeccionRequest p) {
        return ResponseEntity.ok(service.crearLeccion(moduloId, p));
    }

    @PutMapping("/modulos/{id}/lecciones/orden")
    @Operation(summary = "Reordenar las lecciones de un módulo")
    public ResponseEntity<List<LeccionResponse>> reordenarLecciones(
            @PathVariable Long id, @Valid @RequestBody OrdenRequest p) {
        return ResponseEntity.ok(service.reordenarLecciones(id, p));
    }

    @PutMapping("/lecciones/{id}")
    @Operation(summary = "Editar una lección")
    public ResponseEntity<LeccionResponse> actualizarLeccion(
            @PathVariable Long id, @Valid @RequestBody CrearLeccionRequest p) {
        return ResponseEntity.ok(service.actualizarLeccion(id, p));
    }

    @PatchMapping("/lecciones/{id}/activo")
    @Operation(summary = "Activar o desactivar una lección (desactivar nunca borra)")
    public ResponseEntity<LeccionResponse> cambiarActivoLeccion(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoLeccion(id, p.activo()));
    }

    @DeleteMapping("/lecciones/{id}")
    @Operation(summary = "Eliminar una lección", description = "Borrado real, solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarLeccion(@PathVariable Long id) {
        service.eliminarLeccion(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/lecciones/{id}/sesion")
    @Operation(summary = "Programar la sesión de una lección en vivo",
            description = "Fecha, hora y enlace de reunión. Exige modalidad EN_VIVO/HIBRIDO y fechas dentro del periodo del curso.")
    @ApiResponse(responseCode = "400", description = "Lección no es EN_VIVO, curso virtual, horario inválido o fuera del periodo")
    public ResponseEntity<LeccionResponse> actualizarSesion(
            @PathVariable Long id, @Valid @RequestBody ActualizarSesionRequest p) {
        return ResponseEntity.ok(service.actualizarSesion(id, p));
    }

    @PatchMapping("/lecciones/{id}/sesion/reprogramar")
    @Operation(summary = "Reprogramar una sesión en vivo futura",
            description = "HU-027. Exige motivo, conserva el horario anterior en el historial. Si la nueva fecha "
                    + "supera el fin del curso, no guarda nada y pide ampliarlo primero (ver fecha-fin).")
    public ResponseEntity<ReprogramarSesionResultado> reprogramarSesion(
            @PathVariable Long id, @Valid @RequestBody ReprogramarSesionRequest p) {
        return ResponseEntity.ok(administrarSesionesService.reprogramar(id, p));
    }

    @PatchMapping("/cursos/{cursoId}/fecha-fin")
    @Operation(summary = "Ampliar la fecha de fin de un curso",
            description = "HU-027. Paso previo cuando una reprogramación de sesión supera el fin vigente del curso.")
    public ResponseEntity<Void> ampliarFinDeCurso(
            @PathVariable Long cursoId, @RequestParam LocalDate nuevaFechaFin) {
        administrarSesionesService.ampliarFinDeCurso(cursoId, nuevaFechaFin);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lecciones/{id}/sesion/cancelar")
    @Operation(summary = "Cancelar una sesión en vivo futura",
            description = "HU-027. Definitivo: la sesión queda CANCELADA, excluida de asistencia/progreso.")
    public ResponseEntity<Void> cancelarSesion(
            @PathVariable Long id, @Valid @RequestBody CancelarSesionRequest p) {
        administrarSesionesService.cancelar(id, p);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lecciones/{id}/sesion/enlace")
    @Operation(summary = "Corregir el enlace de una sesión ya realizada",
            description = "HU-027. Solo aplica a una sesión que ya pasó; no cambia fechas.")
    public ResponseEntity<Void> corregirEnlaceSesion(
            @PathVariable Long id, @Valid @RequestBody CorregirEnlaceSesionRequest p) {
        administrarSesionesService.corregirEnlace(id, p);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/asistencias/{id}")
    @Operation(summary = "Corregir una asistencia registrada",
            description = "HU-027. Exige motivo; nunca borra el registro original, solo deja el ajuste.")
    public ResponseEntity<Void> corregirAsistencia(
            @PathVariable Long id, @Valid @RequestBody CorregirAsistenciaRequest p) {
        administrarSesionesService.corregirAsistencia(id, p);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lecciones/{id}/sesion/asistencias")
    @Operation(summary = "Consultar la asistencia de una sesión",
            description = "HU-027. Alumno, estado de asistencia y su identificador, para el modal de corrección.")
    public ResponseEntity<List<AsistenciaSesionResponse>> asistenciasSesion(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(administrarSesionesService.listarAsistencias(id));
    }

    @GetMapping("/lecciones/{id}/sesion/historial")
    @Operation(summary = "Consultar el historial de cambios de una sesión",
            description = "HU-027. Reprogramaciones y cancelaciones anteriores, más reciente primero.")
    public ResponseEntity<List<HistorialSesionResponse>> historialSesion(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(administrarSesionesService.historial(id));
    }

    @PostMapping("/lecciones/{id}/materiales")
    @Operation(summary = "Asociar un material sin archivo propio (YouTube o enlace externo)")
    public ResponseEntity<MaterialResponse> crearMaterialEnlace(
            @PathVariable Long id, @Valid @RequestBody CrearMaterialEnlaceRequest p) {
        return ResponseEntity.ok(service.crearMaterialEnlace(id, p));
    }

    @PostMapping(value = "/lecciones/{id}/materiales/archivo", consumes = "multipart/form-data")
    @Operation(summary = "Subir un archivo como material (origen SUBIDO)",
            description = "Valida extensión y tamaño contra las reglas activas de HU-009 antes de guardarlo.")
    @ApiResponse(responseCode = "400", description = "Extensión no permitida o tamaño excedido")
    public ResponseEntity<MaterialResponse> subirMaterialArchivo(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam String titulo,
            @RequestParam Long tipoMaterialId,
            @RequestParam(defaultValue = "false") boolean permiteDescarga) {
        return ResponseEntity.ok(service.subirMaterialArchivo(id, archivo, titulo, tipoMaterialId, permiteDescarga));
    }

    @PutMapping("/lecciones/{id}/materiales/orden")
    @Operation(summary = "Reordenar los materiales de una lección")
    public ResponseEntity<List<MaterialResponse>> reordenarMateriales(
            @PathVariable Long id, @Valid @RequestBody OrdenRequest p) {
        return ResponseEntity.ok(service.reordenarMateriales(id, p));
    }

    @PatchMapping("/materiales/{id}")
    @Operation(summary = "Editar título o permiso de descarga de un material")
    public ResponseEntity<MaterialResponse> actualizarMaterial(
            @PathVariable Long id, @Valid @RequestBody ActualizarMaterialRequest p) {
        return ResponseEntity.ok(service.actualizarMaterial(id, p));
    }

    @PatchMapping("/materiales/{id}/activo")
    @Operation(summary = "Quitar o restaurar un material (nunca borra el recurso)")
    public ResponseEntity<MaterialResponse> cambiarActivoMaterial(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoMaterial(id, p.activo()));
    }

    @DeleteMapping("/materiales/{id}")
    @Operation(summary = "Eliminar un material", description = "Borrado real (nunca del recurso físico), solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarMaterial(@PathVariable Long id) {
        service.eliminarMaterial(id);
        return ResponseEntity.noContent().build();
    }
}
