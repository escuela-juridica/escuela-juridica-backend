package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.ActivoRequest;
import pe.edu.utp.escuela.app.dto.CrearExamenRequest;
import pe.edu.utp.escuela.app.dto.CrearPreguntaRequest;
import pe.edu.utp.escuela.app.dto.ExamenResponse;
import pe.edu.utp.escuela.app.dto.OrdenRequest;
import pe.edu.utp.escuela.app.dto.PreguntaResponse;
import pe.edu.utp.escuela.app.service.ExamenService;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "HU-013 Configurar exámenes", description = "Exámenes, preguntas y alternativas, para administradores")
public class ExamenController {

    private final ExamenService service;

    @GetMapping("/cursos/{cursoId}/examenes")
    @Operation(summary = "Listar los exámenes de un curso, con sus preguntas y alternativas")
    public ResponseEntity<List<ExamenResponse>> listarPorCurso(@PathVariable Long cursoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listarPorCurso(cursoId));
    }

    @PostMapping("/cursos/{cursoId}/examenes")
    @Operation(summary = "Crear un examen de módulo o final")
    @ApiResponse(responseCode = "400", description = "Finalidad/módulo incoherentes o reglas de intentos/habilitación inválidas")
    @ApiResponse(responseCode = "409", description = "Ya existe un examen con ese título en el curso")
    public ResponseEntity<ExamenResponse> crearExamen(
            @PathVariable Long cursoId, @Valid @RequestBody CrearExamenRequest p) {
        return ResponseEntity.ok(service.crearExamen(cursoId, p));
    }

    @PutMapping("/examenes/{id}")
    @Operation(summary = "Editar la configuración de un examen")
    public ResponseEntity<ExamenResponse> actualizarExamen(
            @PathVariable Long id, @Valid @RequestBody CrearExamenRequest p) {
        return ResponseEntity.ok(service.actualizarExamen(id, p));
    }

    @PatchMapping("/examenes/{id}/activo")
    @Operation(summary = "Activar o desactivar un examen (desactivar nunca borra)")
    public ResponseEntity<ExamenResponse> cambiarActivoExamen(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoExamen(id, p.activo()));
    }

    @DeleteMapping("/examenes/{id}")
    @Operation(summary = "Eliminar un examen", description = "Borrado real, solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarExamen(@PathVariable Long id) {
        service.eliminarExamen(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cursos/{cursoId}/examenes/orden")
    @Operation(summary = "Reordenar los exámenes de un curso")
    public ResponseEntity<List<ExamenResponse>> reordenarExamenes(
            @PathVariable Long cursoId, @Valid @RequestBody OrdenRequest p) {
        return ResponseEntity.ok(service.reordenarExamenes(cursoId, p));
    }

    @PostMapping("/examenes/{examenId}/preguntas")
    @Operation(summary = "Agregar una pregunta a un examen")
    @ApiResponse(responseCode = "400", description = "Alternativas insuficientes o inconsistentes con el tipo de pregunta")
    public ResponseEntity<PreguntaResponse> crearPregunta(
            @PathVariable Long examenId, @Valid @RequestBody CrearPreguntaRequest p) {
        return ResponseEntity.ok(service.crearPregunta(examenId, p));
    }

    @PutMapping("/preguntas/{id}")
    @Operation(summary = "Editar una pregunta (reemplaza sus alternativas)")
    public ResponseEntity<PreguntaResponse> actualizarPregunta(
            @PathVariable Long id, @Valid @RequestBody CrearPreguntaRequest p) {
        return ResponseEntity.ok(service.actualizarPregunta(id, p));
    }

    @PatchMapping("/preguntas/{id}/activo")
    @Operation(summary = "Activar o desactivar una pregunta (desactivar nunca borra)")
    public ResponseEntity<PreguntaResponse> cambiarActivoPregunta(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoPregunta(id, p.activo()));
    }

    @DeleteMapping("/preguntas/{id}")
    @Operation(summary = "Eliminar una pregunta", description = "Borrado real, solo mientras el curso sigue en BORRADOR.")
    @ApiResponse(responseCode = "400", description = "El curso ya no está en borrador")
    public ResponseEntity<Void> eliminarPregunta(@PathVariable Long id) {
        service.eliminarPregunta(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/examenes/{examenId}/preguntas/orden")
    @Operation(summary = "Reordenar las preguntas de un examen")
    public ResponseEntity<List<PreguntaResponse>> reordenarPreguntas(
            @PathVariable Long examenId, @Valid @RequestBody OrdenRequest p) {
        return ResponseEntity.ok(service.reordenarPreguntas(examenId, p));
    }
}
