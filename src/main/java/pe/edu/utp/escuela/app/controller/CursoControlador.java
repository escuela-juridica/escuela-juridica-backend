package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.ActualizarInformacionCursoPeticion;
import pe.edu.utp.escuela.app.dto.AsignarDocentesPeticion;
import pe.edu.utp.escuela.app.dto.AsignarFirmantesPeticion;
import pe.edu.utp.escuela.app.dto.CrearCursoPeticion;
import pe.edu.utp.escuela.app.dto.CursoEditorRespuesta;
import pe.edu.utp.escuela.app.dto.CursoResumenRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.service.CursoServicio;

@RestController
@RequestMapping("/api/admin/cursos")
@RequiredArgsConstructor
@Tag(name = "HU-010 Crear curso", description = "Alta y configuración comercial/temporal de cursos, para administradores")
public class CursoControlador {

    private final CursoServicio servicio;

    @GetMapping
    @Operation(summary = "Listar cursos (búsqueda por título, paginado)")
    @ApiResponse(responseCode = "200", description = "Página de cursos")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<PageResponse<CursoResumenRespuesta>> listar(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.listar(texto, PageRequest.of(Math.max(page, 0), tamano)));
    }

    @GetMapping("/beneficios-sugeridos")
    @Operation(summary = "Sugerir beneficios ya usados en otros cursos",
            description = "No hay tabla maestra de beneficios (siguen siendo texto libre por curso); esto solo "
                    + "evita redactar variantes casi idénticas de un curso a otro.")
    @ApiResponse(responseCode = "200", description = "Hasta 10 coincidencias")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<List<String>> sugerirBeneficios(@RequestParam(defaultValue = "") String texto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.sugerirBeneficios(texto));
    }

    @PostMapping
    @Operation(summary = "Crear un curso en BORRADOR", description = "Crea el borrador mínimo con el título; el resto se completa en la pestaña Información.")
    @ApiResponse(responseCode = "200", description = "Borrador creado")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<CursoEditorRespuesta> crear(@Valid @RequestBody CrearCursoPeticion p) {
        return ResponseEntity.ok(servicio.crear(p));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el editor completo del curso")
    @ApiResponse(responseCode = "200", description = "Datos completos del curso")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso ya no existe")
    public ResponseEntity<CursoEditorRespuesta> obtener(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.obtener(id));
    }

    @PutMapping("/{id}/informacion")
    @Operation(summary = "Guardar la información comercial y temporal del curso")
    @ApiResponse(responseCode = "200", description = "Información guardada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o incoherentes para la modalidad")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso o alguna referencia seleccionada ya no existe")
    @ApiResponse(responseCode = "409", description = "La URL amigable ya está en uso")
    public ResponseEntity<CursoEditorRespuesta> actualizarInformacion(
            @PathVariable Long id, @Valid @RequestBody ActualizarInformacionCursoPeticion p) {
        return ResponseEntity.ok(servicio.actualizarInformacion(id, p));
    }

    @PutMapping("/{id}/docentes")
    @Operation(summary = "Reemplazar los docentes asignados y su orden")
    @ApiResponse(responseCode = "200", description = "Docentes actualizados")
    @ApiResponse(responseCode = "400", description = "Docente repetido o inactivo")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso o algún docente ya no existe")
    public ResponseEntity<CursoEditorRespuesta> actualizarDocentes(
            @PathVariable Long id, @Valid @RequestBody AsignarDocentesPeticion p) {
        return ResponseEntity.ok(servicio.actualizarDocentes(id, p));
    }

    @PutMapping("/{id}/firmantes")
    @Operation(summary = "Reemplazar los firmantes asignados y su orden")
    @ApiResponse(responseCode = "200", description = "Firmantes actualizados")
    @ApiResponse(responseCode = "400", description = "Firmante repetido o inactivo")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso o algún firmante ya no existe")
    public ResponseEntity<CursoEditorRespuesta> actualizarFirmantes(
            @PathVariable Long id, @Valid @RequestBody AsignarFirmantesPeticion p) {
        return ResponseEntity.ok(servicio.actualizarFirmantes(id, p));
    }
}
