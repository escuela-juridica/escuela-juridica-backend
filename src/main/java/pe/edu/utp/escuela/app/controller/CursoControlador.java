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
import pe.edu.utp.escuela.app.dto.ActualizarReglasCursoPeticion;
import pe.edu.utp.escuela.app.dto.AsignarDocentesPeticion;
import pe.edu.utp.escuela.app.dto.AsignarFirmantesPeticion;
import pe.edu.utp.escuela.app.dto.CambiarDestacadoPeticion;
import pe.edu.utp.escuela.app.dto.CerrarCursoPeticion;
import pe.edu.utp.escuela.app.dto.CrearCursoPeticion;
import pe.edu.utp.escuela.app.dto.CursoEditorRespuesta;
import pe.edu.utp.escuela.app.dto.CursoResumenRespuesta;
import pe.edu.utp.escuela.app.dto.ReglasCursoRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.RetrasarInicioPeticion;
import pe.edu.utp.escuela.app.dto.ValidacionPublicacionRespuesta;
import pe.edu.utp.escuela.app.service.CicloVidaCursoServicio;
import pe.edu.utp.escuela.app.service.CursoServicio;
import pe.edu.utp.escuela.app.service.PublicacionServicio;

@RestController
@RequestMapping("/api/admin/cursos")
@RequiredArgsConstructor
@Tag(name = "HU-010 Crear curso", description = "Alta y configuración comercial/temporal de cursos, para administradores")
public class CursoControlador {

    private final CursoServicio servicio;
    private final PublicacionServicio publicacion;
    private final CicloVidaCursoServicio cicloVida;

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

    @GetMapping("/{id}/reglas")
    @Operation(summary = "Consultar requisitos académicos y de certificación")
    public ResponseEntity<ReglasCursoRespuesta> obtenerReglas(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.obtenerReglas(id));
    }

    @PutMapping("/{id}/reglas")
    @Operation(summary = "Guardar requisitos académicos y de certificación")
    @ApiResponse(responseCode = "400", description = "Reglas inválidas o ya congeladas")
    public ResponseEntity<ReglasCursoRespuesta> actualizarReglas(
            @PathVariable Long id, @Valid @RequestBody ActualizarReglasCursoPeticion p) {
        return ResponseEntity.ok(servicio.actualizarReglas(id, p));
    }

    @GetMapping("/{id}/validacion")
    @Operation(summary = "Validar un borrador antes de publicarlo",
            description = "Reúne todos los hallazgos a la vez; no modifica el curso.")
    @ApiResponse(responseCode = "200", description = "Lista de hallazgos (vacía si no hay nada que corregir)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso ya no existe")
    public ResponseEntity<ValidacionPublicacionRespuesta> validar(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicacion.validar(id));
    }

    @PostMapping("/{id}/publicacion")
    @Operation(summary = "Publicar un curso en BORRADOR",
            description = "Repite la validación dentro de la misma transacción; si no hay bloqueos, pasa a "
                    + "PUBLICADO (o directamente a EN_CURSO si es virtual sin fecha de inicio).")
    @ApiResponse(responseCode = "200", description = "Resultado de la publicación (puede haber quedado bloqueada)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "El curso ya no existe")
    public ResponseEntity<ValidacionPublicacionRespuesta> publicar(@PathVariable Long id) {
        return ResponseEntity.ok(publicacion.publicar(id));
    }

    @PostMapping("/{id}/adelantar-inicio")
    @Operation(summary = "Adelantar el inicio de un curso publicado",
            description = "Pasa de PUBLICADO a EN_CURSO de inmediato, sin esperar la fecha programada.")
    @ApiResponse(responseCode = "200", description = "Curso en curso")
    @ApiResponse(responseCode = "400", description = "El curso no está publicado")
    public ResponseEntity<CursoEditorRespuesta> adelantarInicio(@PathVariable Long id) {
        return ResponseEntity.ok(cicloVida.adelantarInicio(id));
    }

    @PostMapping("/{id}/retrasar-inicio")
    @Operation(summary = "Retrasar la fecha de inicio de un curso publicado",
            description = "Solo antes de que el curso inicie; la nueva fecha debe ser posterior a la actual.")
    @ApiResponse(responseCode = "200", description = "Fecha actualizada")
    @ApiResponse(responseCode = "400", description = "El curso no está publicado o la fecha no es válida")
    public ResponseEntity<CursoEditorRespuesta> retrasarInicio(
            @PathVariable Long id, @Valid @RequestBody RetrasarInicioPeticion p) {
        return ResponseEntity.ok(cicloVida.retrasarInicio(id, p));
    }

    @PostMapping("/{id}/cerrar")
    @Operation(summary = "Cerrar anticipadamente un curso publicado o en curso",
            description = "No cancela matrículas ni retira el acceso de quienes ya cursan; solo deja de ofrecerse.")
    @ApiResponse(responseCode = "200", description = "Curso cerrado")
    @ApiResponse(responseCode = "400", description = "El curso no está publicado ni en curso")
    public ResponseEntity<CursoEditorRespuesta> cerrar(
            @PathVariable Long id, @RequestBody(required = false) CerrarCursoPeticion p) {
        return ResponseEntity.ok(cicloVida.cerrar(id, p != null ? p : new CerrarCursoPeticion(null)));
    }

    @PatchMapping("/{id}/destacado")
    @Operation(summary = "Destacar o quitar del destacado para el orden del catálogo")
    @ApiResponse(responseCode = "200", description = "Destacado actualizado")
    public ResponseEntity<CursoEditorRespuesta> cambiarDestacado(
            @PathVariable Long id, @Valid @RequestBody CambiarDestacadoPeticion p) {
        return ResponseEntity.ok(cicloVida.cambiarDestacado(id, p.destacado()));
    }

    @PostMapping("/{id}/duplicar")
    @Operation(summary = "Duplicar como una nueva convocatoria BORRADOR",
            description = "Copia información general, contenido, exámenes y reglas; no copia matrículas, pagos, "
                    + "progreso, intentos, asistencia ni certificados. La copia recibe una URL amigable propia.")
    @ApiResponse(responseCode = "200", description = "Nuevo borrador creado")
    public ResponseEntity<CursoEditorRespuesta> duplicar(@PathVariable Long id) {
        return ResponseEntity.ok(cicloVida.duplicar(id));
    }
}
