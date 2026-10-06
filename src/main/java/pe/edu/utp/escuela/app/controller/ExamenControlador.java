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
import pe.edu.utp.escuela.app.dto.ActivoPeticion;
import pe.edu.utp.escuela.app.dto.CrearExamenPeticion;
import pe.edu.utp.escuela.app.dto.CrearPreguntaPeticion;
import pe.edu.utp.escuela.app.dto.ExamenRespuesta;
import pe.edu.utp.escuela.app.dto.OrdenPeticion;
import pe.edu.utp.escuela.app.dto.PreguntaRespuesta;
import pe.edu.utp.escuela.app.service.ExamenServicio;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "HU-013 Configurar exámenes", description = "Exámenes, preguntas y alternativas, para administradores")
public class ExamenControlador {

    private final ExamenServicio servicio;

    @GetMapping("/cursos/{cursoId}/examenes")
    @Operation(summary = "Listar los exámenes de un curso, con sus preguntas y alternativas")
    public ResponseEntity<List<ExamenRespuesta>> listarPorCurso(@PathVariable Long cursoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listarPorCurso(cursoId));
    }

    @PostMapping("/cursos/{cursoId}/examenes")
    @Operation(summary = "Crear un examen de módulo o final")
    @ApiResponse(responseCode = "400", description = "Finalidad/módulo incoherentes o reglas de intentos/habilitación inválidas")
    @ApiResponse(responseCode = "409", description = "Ya existe un examen con ese título en el curso")
    public ResponseEntity<ExamenRespuesta> crearExamen(
            @PathVariable Long cursoId, @Valid @RequestBody CrearExamenPeticion p) {
        return ResponseEntity.ok(servicio.crearExamen(cursoId, p));
    }

    @PutMapping("/examenes/{id}")
    @Operation(summary = "Editar la configuración de un examen")
    public ResponseEntity<ExamenRespuesta> actualizarExamen(
            @PathVariable Long id, @Valid @RequestBody CrearExamenPeticion p) {
        return ResponseEntity.ok(servicio.actualizarExamen(id, p));
    }

    @PatchMapping("/examenes/{id}/activo")
    @Operation(summary = "Activar o desactivar un examen (desactivar nunca borra)")
    public ResponseEntity<ExamenRespuesta> cambiarActivoExamen(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(servicio.cambiarActivoExamen(id, p.activo()));
    }

    @PutMapping("/cursos/{cursoId}/examenes/orden")
    @Operation(summary = "Reordenar los exámenes de un curso")
    public ResponseEntity<List<ExamenRespuesta>> reordenarExamenes(
            @PathVariable Long cursoId, @Valid @RequestBody OrdenPeticion p) {
        return ResponseEntity.ok(servicio.reordenarExamenes(cursoId, p));
    }

    @PostMapping("/examenes/{examenId}/preguntas")
    @Operation(summary = "Agregar una pregunta a un examen")
    @ApiResponse(responseCode = "400", description = "Alternativas insuficientes o inconsistentes con el tipo de pregunta")
    public ResponseEntity<PreguntaRespuesta> crearPregunta(
            @PathVariable Long examenId, @Valid @RequestBody CrearPreguntaPeticion p) {
        return ResponseEntity.ok(servicio.crearPregunta(examenId, p));
    }

    @PutMapping("/preguntas/{id}")
    @Operation(summary = "Editar una pregunta (reemplaza sus alternativas)")
    public ResponseEntity<PreguntaRespuesta> actualizarPregunta(
            @PathVariable Long id, @Valid @RequestBody CrearPreguntaPeticion p) {
        return ResponseEntity.ok(servicio.actualizarPregunta(id, p));
    }

    @PatchMapping("/preguntas/{id}/activo")
    @Operation(summary = "Activar o desactivar una pregunta (desactivar nunca borra)")
    public ResponseEntity<PreguntaRespuesta> cambiarActivoPregunta(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(servicio.cambiarActivoPregunta(id, p.activo()));
    }

    @PutMapping("/examenes/{examenId}/preguntas/orden")
    @Operation(summary = "Reordenar las preguntas de un examen")
    public ResponseEntity<List<PreguntaRespuesta>> reordenarPreguntas(
            @PathVariable Long examenId, @Valid @RequestBody OrdenPeticion p) {
        return ResponseEntity.ok(servicio.reordenarPreguntas(examenId, p));
    }
}
