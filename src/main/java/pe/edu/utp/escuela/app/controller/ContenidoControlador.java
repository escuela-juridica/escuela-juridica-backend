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
import pe.edu.utp.escuela.app.dto.ActivoPeticion;
import pe.edu.utp.escuela.app.dto.ActualizarMaterialPeticion;
import pe.edu.utp.escuela.app.dto.AsistenciaSesionRespuesta;
import pe.edu.utp.escuela.app.dto.ActualizarSesionPeticion;
import pe.edu.utp.escuela.app.dto.CancelarSesionPeticion;
import pe.edu.utp.escuela.app.dto.CorregirAsistenciaPeticion;
import pe.edu.utp.escuela.app.dto.CorregirEnlaceSesionPeticion;
import pe.edu.utp.escuela.app.dto.CrearLeccionPeticion;
import pe.edu.utp.escuela.app.dto.CrearMaterialEnlacePeticion;
import pe.edu.utp.escuela.app.dto.CrearModuloPeticion;
import pe.edu.utp.escuela.app.dto.HistorialSesionRespuesta;
import pe.edu.utp.escuela.app.dto.LeccionRespuesta;
import pe.edu.utp.escuela.app.dto.MaterialRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloDisponibleRespuesta;
import pe.edu.utp.escuela.app.dto.ModuloRespuesta;
import pe.edu.utp.escuela.app.dto.OrdenPeticion;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionPeticion;
import pe.edu.utp.escuela.app.dto.ReprogramarSesionResultado;
import pe.edu.utp.escuela.app.service.AdministrarSesionesServicio;
import pe.edu.utp.escuela.app.service.ContenidoServicio;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "HU-011 Contenido del curso", description = "Módulos, lecciones, materiales y sesiones en vivo (HU-012), para administradores")
public class ContenidoControlador {

    private final ContenidoServicio servicio;
    private final AdministrarSesionesServicio administrarSesionesServicio;

    @GetMapping("/cursos/{cursoId}/modulos")
    @Operation(summary = "Consultar la estructura completa de contenido de un curso")
    public ResponseEntity<List<ModuloRespuesta>> obtenerEstructura(@PathVariable Long cursoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.obtenerEstructura(cursoId));
    }

    @GetMapping("/modulos-disponibles")
    @Operation(summary = "Buscar módulos de cualquier curso para copiarlos",
            description = "Usado por \"agregar módulo existente\": la copia es completa e independiente.")
    public ResponseEntity<List<ModuloDisponibleRespuesta>> listarModulosDisponibles(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long excluirCursoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.listarModulosDisponibles(texto, excluirCursoId));
    }

    @PostMapping("/cursos/{cursoId}/modulos")
    @Operation(summary = "Crear un módulo al final del curso")
    public ResponseEntity<ModuloRespuesta> crearModulo(
            @PathVariable Long cursoId, @Valid @RequestBody CrearModuloPeticion p) {
        return ResponseEntity.ok(servicio.crearModulo(cursoId, p));
    }

    @PostMapping("/cursos/{cursoId}/copias-modulo/{moduloOrigenId}")
    @Operation(summary = "Copiar un módulo existente dentro de este curso",
            description = "Copia módulos, lecciones, materiales y exámenes de módulo; los materiales reutilizan el mismo recurso físico.")
    @ApiResponse(responseCode = "404", description = "El curso o el módulo de origen ya no existen")
    public ResponseEntity<ModuloRespuesta> copiarModulo(
            @PathVariable Long cursoId, @PathVariable Long moduloOrigenId) {
        return ResponseEntity.ok(servicio.copiarModulo(cursoId, moduloOrigenId));
    }

    @PutMapping("/cursos/{cursoId}/modulos/orden")
    @Operation(summary = "Reordenar los módulos de un curso")
    public ResponseEntity<List<ModuloRespuesta>> reordenarModulos(
            @PathVariable Long cursoId, @Valid @RequestBody OrdenPeticion p) {
        return ResponseEntity.ok(servicio.reordenarModulos(cursoId, p));
    }

    @PutMapping("/modulos/{id}")
    @Operation(summary = "Editar título y descripción de un módulo")
    public ResponseEntity<ModuloRespuesta> actualizarModulo(
            @PathVariable Long id, @Valid @RequestBody CrearModuloPeticion p) {
        return ResponseEntity.ok(servicio.actualizarModulo(id, p));
    }

    @PatchMapping("/modulos/{id}/activo")
    @Operation(summary = "Activar o desactivar un módulo (desactivar nunca borra)")
    public ResponseEntity<ModuloRespuesta> cambiarActivoModulo(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(servicio.cambiarActivoModulo(id, p.activo()));
    }

    @DeleteMapping("/modulos/{id}")
    @Operation(summary = "Eliminar un módulo", description = "Borrado real, solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarModulo(@PathVariable Long id) {
        servicio.eliminarModulo(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/modulos/{moduloId}/lecciones")
    @Operation(summary = "Crear una lección al final del módulo")
    public ResponseEntity<LeccionRespuesta> crearLeccion(
            @PathVariable Long moduloId, @Valid @RequestBody CrearLeccionPeticion p) {
        return ResponseEntity.ok(servicio.crearLeccion(moduloId, p));
    }

    @PutMapping("/modulos/{id}/lecciones/orden")
    @Operation(summary = "Reordenar las lecciones de un módulo")
    public ResponseEntity<List<LeccionRespuesta>> reordenarLecciones(
            @PathVariable Long id, @Valid @RequestBody OrdenPeticion p) {
        return ResponseEntity.ok(servicio.reordenarLecciones(id, p));
    }

    @PutMapping("/lecciones/{id}")
    @Operation(summary = "Editar una lección")
    public ResponseEntity<LeccionRespuesta> actualizarLeccion(
            @PathVariable Long id, @Valid @RequestBody CrearLeccionPeticion p) {
        return ResponseEntity.ok(servicio.actualizarLeccion(id, p));
    }

    @PatchMapping("/lecciones/{id}/activo")
    @Operation(summary = "Activar o desactivar una lección (desactivar nunca borra)")
    public ResponseEntity<LeccionRespuesta> cambiarActivoLeccion(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(servicio.cambiarActivoLeccion(id, p.activo()));
    }

    @DeleteMapping("/lecciones/{id}")
    @Operation(summary = "Eliminar una lección", description = "Borrado real, solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarLeccion(@PathVariable Long id) {
        servicio.eliminarLeccion(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/lecciones/{id}/sesion")
    @Operation(summary = "Programar la sesión de una lección en vivo",
            description = "Fecha, hora y enlace de reunión. Exige modalidad EN_VIVO/HIBRIDO y fechas dentro del periodo del curso.")
    @ApiResponse(responseCode = "400", description = "Lección no es EN_VIVO, curso virtual, horario inválido o fuera del periodo")
    public ResponseEntity<LeccionRespuesta> actualizarSesion(
            @PathVariable Long id, @Valid @RequestBody ActualizarSesionPeticion p) {
        return ResponseEntity.ok(servicio.actualizarSesion(id, p));
    }

    @PatchMapping("/lecciones/{id}/sesion/reprogramar")
    @Operation(summary = "Reprogramar una sesión en vivo futura",
            description = "HU-027. Exige motivo, conserva el horario anterior en el historial. Si la nueva fecha "
                    + "supera el fin del curso, no guarda nada y pide ampliarlo primero (ver fecha-fin).")
    public ResponseEntity<ReprogramarSesionResultado> reprogramarSesion(
            @PathVariable Long id, @Valid @RequestBody ReprogramarSesionPeticion p) {
        return ResponseEntity.ok(administrarSesionesServicio.reprogramar(id, p));
    }

    @PatchMapping("/cursos/{cursoId}/fecha-fin")
    @Operation(summary = "Ampliar la fecha de fin de un curso",
            description = "HU-027. Paso previo cuando una reprogramación de sesión supera el fin vigente del curso.")
    public ResponseEntity<Void> ampliarFinDeCurso(
            @PathVariable Long cursoId, @RequestParam LocalDate nuevaFechaFin) {
        administrarSesionesServicio.ampliarFinDeCurso(cursoId, nuevaFechaFin);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lecciones/{id}/sesion/cancelar")
    @Operation(summary = "Cancelar una sesión en vivo futura",
            description = "HU-027. Definitivo: la sesión queda CANCELADA, excluida de asistencia/progreso.")
    public ResponseEntity<Void> cancelarSesion(
            @PathVariable Long id, @Valid @RequestBody CancelarSesionPeticion p) {
        administrarSesionesServicio.cancelar(id, p);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lecciones/{id}/sesion/enlace")
    @Operation(summary = "Corregir el enlace de una sesión ya realizada",
            description = "HU-027. Solo aplica a una sesión que ya pasó; no cambia fechas.")
    public ResponseEntity<Void> corregirEnlaceSesion(
            @PathVariable Long id, @Valid @RequestBody CorregirEnlaceSesionPeticion p) {
        administrarSesionesServicio.corregirEnlace(id, p);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/asistencias/{id}")
    @Operation(summary = "Corregir una asistencia registrada",
            description = "HU-027. Exige motivo; nunca borra el registro original, solo deja el ajuste.")
    public ResponseEntity<Void> corregirAsistencia(
            @PathVariable Long id, @Valid @RequestBody CorregirAsistenciaPeticion p) {
        administrarSesionesServicio.corregirAsistencia(id, p);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lecciones/{id}/sesion/asistencias")
    @Operation(summary = "Consultar la asistencia de una sesión",
            description = "HU-027. Alumno, estado de asistencia y su identificador, para el modal de corrección.")
    public ResponseEntity<List<AsistenciaSesionRespuesta>> asistenciasSesion(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(administrarSesionesServicio.listarAsistencias(id));
    }

    @GetMapping("/lecciones/{id}/sesion/historial")
    @Operation(summary = "Consultar el historial de cambios de una sesión",
            description = "HU-027. Reprogramaciones y cancelaciones anteriores, más reciente primero.")
    public ResponseEntity<List<HistorialSesionRespuesta>> historialSesion(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(administrarSesionesServicio.historial(id));
    }

    @PostMapping("/lecciones/{id}/materiales")
    @Operation(summary = "Asociar un material sin archivo propio (YouTube o enlace externo)")
    public ResponseEntity<MaterialRespuesta> crearMaterialEnlace(
            @PathVariable Long id, @Valid @RequestBody CrearMaterialEnlacePeticion p) {
        return ResponseEntity.ok(servicio.crearMaterialEnlace(id, p));
    }

    @PostMapping(value = "/lecciones/{id}/materiales/archivo", consumes = "multipart/form-data")
    @Operation(summary = "Subir un archivo como material (origen SUBIDO)",
            description = "Valida extensión y tamaño contra las reglas activas de HU-009 antes de guardarlo.")
    @ApiResponse(responseCode = "400", description = "Extensión no permitida o tamaño excedido")
    public ResponseEntity<MaterialRespuesta> subirMaterialArchivo(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam String titulo,
            @RequestParam Long tipoMaterialId,
            @RequestParam(defaultValue = "false") boolean permiteDescarga) {
        return ResponseEntity.ok(servicio.subirMaterialArchivo(id, archivo, titulo, tipoMaterialId, permiteDescarga));
    }

    @PutMapping("/lecciones/{id}/materiales/orden")
    @Operation(summary = "Reordenar los materiales de una lección")
    public ResponseEntity<List<MaterialRespuesta>> reordenarMateriales(
            @PathVariable Long id, @Valid @RequestBody OrdenPeticion p) {
        return ResponseEntity.ok(servicio.reordenarMateriales(id, p));
    }

    @PatchMapping("/materiales/{id}")
    @Operation(summary = "Editar título o permiso de descarga de un material")
    public ResponseEntity<MaterialRespuesta> actualizarMaterial(
            @PathVariable Long id, @Valid @RequestBody ActualizarMaterialPeticion p) {
        return ResponseEntity.ok(servicio.actualizarMaterial(id, p));
    }

    @PatchMapping("/materiales/{id}/activo")
    @Operation(summary = "Quitar o restaurar un material (nunca borra el recurso)")
    public ResponseEntity<MaterialRespuesta> cambiarActivoMaterial(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(servicio.cambiarActivoMaterial(id, p.activo()));
    }

    @DeleteMapping("/materiales/{id}")
    @Operation(summary = "Eliminar un material", description = "Borrado real (nunca del recurso físico), solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarMaterial(@PathVariable Long id) {
        servicio.eliminarMaterial(id);
        return ResponseEntity.noContent().build();
    }
}
