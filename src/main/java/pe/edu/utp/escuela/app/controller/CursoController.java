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
import pe.edu.utp.escuela.app.dto.ActualizarInformacionCursoRequest;
import pe.edu.utp.escuela.app.dto.ActualizarReglasCursoRequest;
import pe.edu.utp.escuela.app.dto.AsignarDocentesRequest;
import pe.edu.utp.escuela.app.dto.AsignarFirmantesRequest;
import pe.edu.utp.escuela.app.dto.CambiarDestacadoRequest;
import pe.edu.utp.escuela.app.dto.CerrarCursoRequest;
import pe.edu.utp.escuela.app.dto.CrearCursoRequest;
import pe.edu.utp.escuela.app.dto.CursoEditorResponse;
import pe.edu.utp.escuela.app.dto.CursoResumenResponse;
import pe.edu.utp.escuela.app.dto.ReglasCursoResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.RetrasarInicioRequest;
import pe.edu.utp.escuela.app.dto.ValidacionPublicacionResponse;
import pe.edu.utp.escuela.app.service.CicloVidaCursoService;
import pe.edu.utp.escuela.app.service.CursoService;
import pe.edu.utp.escuela.app.service.PublicacionService;

@RestController
@RequestMapping("/api/admin/cursos")
@RequiredArgsConstructor
@Tag(name = "HU-010 Crear curso", description = "Alta y configuración comercial/temporal de cursos, para administradores")
public class CursoController {

    private final CursoService service;
    private final PublicacionService publicacion;
    private final CicloVidaCursoService cicloVida;

    @GetMapping
    @Operation(summary = "Listar cursos (búsqueda por título, paginado)")
    @ApiResponse(responseCode = "200", description = "Página de cursos")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<PageResponse<CursoResumenResponse>> listar(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(texto, PageRequest.of(Math.max(page, 0), tamano)));
    }

    @GetMapping("/beneficios-sugeridos")
    @Operation(summary = "Sugerir beneficios ya usados en otros cursos",
            description = "No hay tabla maestra de beneficios (siguen siendo texto libre por curso); esto solo "
                    + "evita redactar variantes casi idénticas de un curso a otro.")
    @ApiResponse(responseCode = "200", description = "Hasta 10 coincidencias")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<List<String>> sugerirBeneficios(@RequestParam(defaultValue = "") String texto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.sugerirBeneficios(texto));
    }

    @PostMapping
    @Operation(summary = "Crear un curso en BORRADOR", description = "Crea el borrador mínimo con el título; el resto se completa en la pestaña Información.")
    @ApiResponse(responseCode = "200", description = "Borrador creado")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<CursoEditorResponse> crear(@Valid @RequestBody CrearCursoRequest p) {
        return ResponseEntity.ok(service.crear(p));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el editor completo del curso")
    @ApiResponse(responseCode = "200", description = "Datos completos del curso")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso ya no existe")
    public ResponseEntity<CursoEditorResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.obtener(id));
    }

    @PutMapping("/{id}/informacion")
    @Operation(summary = "Guardar la información comercial y temporal del curso")
    @ApiResponse(responseCode = "200", description = "Información guardada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o incoherentes para la modalidad")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso o alguna referencia seleccionada ya no existe")
    @ApiResponse(responseCode = "409", description = "La URL amigable ya está en uso")
    public ResponseEntity<CursoEditorResponse> actualizarInformacion(
            @PathVariable Long id, @Valid @RequestBody ActualizarInformacionCursoRequest p) {
        return ResponseEntity.ok(service.actualizarInformacion(id, p));
    }

    @PutMapping("/{id}/docentes")
    @Operation(summary = "Reemplazar los docentes asignados y su orden")
    @ApiResponse(responseCode = "200", description = "Docentes actualizados")
    @ApiResponse(responseCode = "400", description = "Docente repetido o inactivo")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso o algún docente ya no existe")
    public ResponseEntity<CursoEditorResponse> actualizarDocentes(
            @PathVariable Long id, @Valid @RequestBody AsignarDocentesRequest p) {
        return ResponseEntity.ok(service.actualizarDocentes(id, p));
    }

    @PutMapping("/{id}/firmantes")
    @Operation(summary = "Reemplazar los firmantes asignados y su orden")
    @ApiResponse(responseCode = "200", description = "Firmantes actualizados")
    @ApiResponse(responseCode = "400", description = "Firmante repetido o inactivo")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso o algún firmante ya no existe")
    public ResponseEntity<CursoEditorResponse> actualizarFirmantes(
            @PathVariable Long id, @Valid @RequestBody AsignarFirmantesRequest p) {
        return ResponseEntity.ok(service.actualizarFirmantes(id, p));
    }

    @GetMapping("/{id}/reglas")
    @Operation(summary = "Consultar requisitos académicos y de certificación")
    public ResponseEntity<ReglasCursoResponse> obtenerReglas(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.obtenerReglas(id));
    }

    @PutMapping("/{id}/reglas")
    @Operation(summary = "Guardar requisitos académicos y de certificación")
    @ApiResponse(responseCode = "400", description = "Reglas inválidas o ya congeladas")
    public ResponseEntity<ReglasCursoResponse> actualizarReglas(
            @PathVariable Long id, @Valid @RequestBody ActualizarReglasCursoRequest p) {
        return ResponseEntity.ok(service.actualizarReglas(id, p));
    }

    @GetMapping("/{id}/validacion")
    @Operation(summary = "Validar un borrador antes de publicarlo",
            description = "Reúne todos los hallazgos a la vez; no modifica el curso.")
    @ApiResponse(responseCode = "200", description = "Lista de hallazgos (vacía si no hay nada que corregir)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso ya no existe")
    public ResponseEntity<ValidacionPublicacionResponse> validar(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicacion.validar(id));
    }

    @PostMapping("/{id}/publicacion")
    @Operation(summary = "Publicar un curso en BORRADOR",
            description = "Repite la validación dentro de la misma transacción; si no hay bloqueos, pasa a "
                    + "PUBLICADO (o directamente a EN_CURSO si es virtual sin fecha de inicio).")
    @ApiResponse(responseCode = "200", description = "Resultado de la publicación (puede haber quedado bloqueada)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso ya no existe")
    public ResponseEntity<ValidacionPublicacionResponse> publicar(@PathVariable Long id) {
        return ResponseEntity.ok(publicacion.publicar(id));
    }

    @PostMapping("/{id}/adelantar-inicio")
    @Operation(summary = "Adelantar el inicio de un curso publicado",
            description = "Pasa de PUBLICADO a EN_CURSO de inmediato, sin esperar la fecha programada.")
    @ApiResponse(responseCode = "200", description = "Curso en curso")
    @ApiResponse(responseCode = "400", description = "El curso no está publicado")
    public ResponseEntity<CursoEditorResponse> adelantarInicio(@PathVariable Long id) {
        return ResponseEntity.ok(cicloVida.adelantarInicio(id));
    }

    @PostMapping("/{id}/retrasar-inicio")
    @Operation(summary = "Retrasar la fecha de inicio de un curso publicado",
            description = "Solo antes de que el curso inicie; la nueva fecha debe ser posterior a la actual.")
    @ApiResponse(responseCode = "200", description = "Fecha actualizada")
    @ApiResponse(responseCode = "400", description = "El curso no está publicado o la fecha no es válida")
    public ResponseEntity<CursoEditorResponse> retrasarInicio(
            @PathVariable Long id, @Valid @RequestBody RetrasarInicioRequest p) {
        return ResponseEntity.ok(cicloVida.retrasarInicio(id, p));
    }

    @PostMapping("/{id}/cerrar")
    @Operation(summary = "Cerrar anticipadamente un curso publicado o en curso",
            description = "No cancela matrículas ni retira el acceso de quienes ya cursan; solo deja de ofrecerse.")
    @ApiResponse(responseCode = "200", description = "Curso cerrado")
    @ApiResponse(responseCode = "400", description = "El curso no está publicado ni en curso")
    public ResponseEntity<CursoEditorResponse> cerrar(
            @PathVariable Long id, @RequestBody(required = false) CerrarCursoRequest p) {
        return ResponseEntity.ok(cicloVida.cerrar(id, p != null ? p : new CerrarCursoRequest(null)));
    }

    @PatchMapping("/{id}/destacado")
    @Operation(summary = "Destacar o quitar del destacado para el orden del catálogo")
    @ApiResponse(responseCode = "200", description = "Destacado actualizado")
    public ResponseEntity<CursoEditorResponse> cambiarDestacado(
            @PathVariable Long id, @Valid @RequestBody CambiarDestacadoRequest p) {
        return ResponseEntity.ok(cicloVida.cambiarDestacado(id, p.destacado()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un curso en borrador",
            description = "Borrado real (no soft-delete), solo permitido mientras el curso sigue en BORRADOR y sin matrículas.")
    @ApiResponse(responseCode = "204", description = "Curso eliminado")
    @ApiResponse(responseCode = "400", description = "El curso no está en borrador, o tiene matrículas")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        cicloVida.eliminarCurso(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicar")
    @Operation(summary = "Duplicar como una nueva convocatoria BORRADOR",
            description = "Copia información general, contenido, exámenes y reglas; no copia matrículas, pagos, "
                    + "progreso, intentos, asistencia ni certificados. La copia recibe una URL amigable propia.")
    @ApiResponse(responseCode = "200", description = "Nuevo borrador creado")
    public ResponseEntity<CursoEditorResponse> duplicar(@PathVariable Long id) {
        return ResponseEntity.ok(cicloVida.duplicar(id));
    }
}
