package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.ActivoPeticion;
import pe.edu.utp.escuela.app.dto.ActualizarConfiguracionPeticion;
import pe.edu.utp.escuela.app.dto.CategoriaPeticion;
import pe.edu.utp.escuela.app.dto.CategoriaRespuesta;
import pe.edu.utp.escuela.app.dto.ConfiguracionPeticion;
import pe.edu.utp.escuela.app.dto.ConfiguracionRespuesta;
import pe.edu.utp.escuela.app.dto.DocentePeticion;
import pe.edu.utp.escuela.app.dto.DocenteRespuesta;
import pe.edu.utp.escuela.app.dto.EntidadPeticion;
import pe.edu.utp.escuela.app.dto.EntidadRespuesta;
import pe.edu.utp.escuela.app.dto.FirmantePeticion;
import pe.edu.utp.escuela.app.dto.FirmanteRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReglaArchivoPeticion;
import pe.edu.utp.escuela.app.dto.ReglaArchivoRespuesta;
import pe.edu.utp.escuela.app.dto.TipoCursoPeticion;
import pe.edu.utp.escuela.app.dto.TipoCursoRespuesta;
import pe.edu.utp.escuela.app.dto.TipoMaterialPeticion;
import pe.edu.utp.escuela.app.dto.TipoMaterialRespuesta;
import pe.edu.utp.escuela.app.service.InformacionBaseService;

/** HU-009 — Administrar información base: datos maestros reutilizados por HU-010 en adelante. */
@RestController
@RequestMapping("/api/admin/informacion-base")
@RequiredArgsConstructor
@Tag(name = "HU-009 Información base", description = "Datos maestros: tipos de curso, categorías, docentes, entidades, firmantes, material y configuración")
public class InformacionBaseController {

    private final InformacionBaseService service;

    // ---- Tipos de curso ----
    @GetMapping("/tipos-curso")
    public ResponseEntity<PageResponse<TipoCursoRespuesta>> listarTiposCurso(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarTiposCurso(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/tipos-curso")
    public ResponseEntity<TipoCursoRespuesta> crearTipoCurso(@Valid @RequestBody TipoCursoPeticion p) {
        return ResponseEntity.ok(service.crearTipoCurso(p));
    }

    @PutMapping("/tipos-curso/{id}")
    public ResponseEntity<TipoCursoRespuesta> actualizarTipoCurso(
            @PathVariable Long id, @Valid @RequestBody TipoCursoPeticion p) {
        return ResponseEntity.ok(service.actualizarTipoCurso(id, p));
    }

    @PatchMapping("/tipos-curso/{id}/activo")
    public ResponseEntity<TipoCursoRespuesta> cambiarActivoTipoCurso(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(service.cambiarActivoTipoCurso(id, p.activo()));
    }

    // ---- Categorías temáticas ----
    @GetMapping("/categorias")
    public ResponseEntity<PageResponse<CategoriaRespuesta>> listarCategorias(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarCategorias(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/categorias")
    public ResponseEntity<CategoriaRespuesta> crearCategoria(@Valid @RequestBody CategoriaPeticion p) {
        return ResponseEntity.ok(service.crearCategoria(p));
    }

    @PutMapping("/categorias/{id}")
    public ResponseEntity<CategoriaRespuesta> actualizarCategoria(
            @PathVariable Long id, @Valid @RequestBody CategoriaPeticion p) {
        return ResponseEntity.ok(service.actualizarCategoria(id, p));
    }

    @PatchMapping("/categorias/{id}/activo")
    public ResponseEntity<CategoriaRespuesta> cambiarActivoCategoria(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(service.cambiarActivoCategoria(id, p.activo()));
    }

    // ---- Docentes ----
    @GetMapping("/docentes")
    public ResponseEntity<PageResponse<DocenteRespuesta>> listarDocentes(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarDocentes(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/docentes")
    public ResponseEntity<DocenteRespuesta> crearDocente(@Valid @RequestBody DocentePeticion p) {
        return ResponseEntity.ok(service.crearDocente(p));
    }

    @PutMapping("/docentes/{id}")
    public ResponseEntity<DocenteRespuesta> actualizarDocente(
            @PathVariable Long id, @Valid @RequestBody DocentePeticion p) {
        return ResponseEntity.ok(service.actualizarDocente(id, p));
    }

    @PatchMapping("/docentes/{id}/activo")
    public ResponseEntity<DocenteRespuesta> cambiarActivoDocente(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(service.cambiarActivoDocente(id, p.activo()));
    }

    // ---- Entidades certificadoras ----
    @GetMapping("/entidades")
    public ResponseEntity<PageResponse<EntidadRespuesta>> listarEntidades(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarEntidades(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/entidades")
    public ResponseEntity<EntidadRespuesta> crearEntidad(@Valid @RequestBody EntidadPeticion p) {
        return ResponseEntity.ok(service.crearEntidad(p));
    }

    @PutMapping("/entidades/{id}")
    public ResponseEntity<EntidadRespuesta> actualizarEntidad(
            @PathVariable Long id, @Valid @RequestBody EntidadPeticion p) {
        return ResponseEntity.ok(service.actualizarEntidad(id, p));
    }

    @PatchMapping("/entidades/{id}/activo")
    public ResponseEntity<EntidadRespuesta> cambiarActivoEntidad(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(service.cambiarActivoEntidad(id, p.activo()));
    }

    // ---- Firmantes ----
    @GetMapping("/firmantes")
    public ResponseEntity<PageResponse<FirmanteRespuesta>> listarFirmantes(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarFirmantes(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/firmantes")
    public ResponseEntity<FirmanteRespuesta> crearFirmante(@Valid @RequestBody FirmantePeticion p) {
        return ResponseEntity.ok(service.crearFirmante(p));
    }

    @PutMapping("/firmantes/{id}")
    public ResponseEntity<FirmanteRespuesta> actualizarFirmante(
            @PathVariable Long id, @Valid @RequestBody FirmantePeticion p) {
        return ResponseEntity.ok(service.actualizarFirmante(id, p));
    }

    @PatchMapping("/firmantes/{id}/activo")
    public ResponseEntity<FirmanteRespuesta> cambiarActivoFirmante(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(service.cambiarActivoFirmante(id, p.activo()));
    }

    // ---- Tipos de material ----
    @GetMapping("/tipos-material")
    public ResponseEntity<PageResponse<TipoMaterialRespuesta>> listarTiposMaterial(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarTiposMaterial(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/tipos-material")
    public ResponseEntity<TipoMaterialRespuesta> crearTipoMaterial(@Valid @RequestBody TipoMaterialPeticion p) {
        return ResponseEntity.ok(service.crearTipoMaterial(p));
    }

    @PutMapping("/tipos-material/{id}")
    public ResponseEntity<TipoMaterialRespuesta> actualizarTipoMaterial(
            @PathVariable Long id, @Valid @RequestBody TipoMaterialPeticion p) {
        return ResponseEntity.ok(service.actualizarTipoMaterial(id, p));
    }

    @PatchMapping("/tipos-material/{id}/activo")
    public ResponseEntity<TipoMaterialRespuesta> cambiarActivoTipoMaterial(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(service.cambiarActivoTipoMaterial(id, p.activo()));
    }

    // ---- Reglas de archivo ----
    @GetMapping("/reglas-archivo")
    public ResponseEntity<List<ReglaArchivoRespuesta>> listarReglasArchivo(
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return sinCache(service.listarReglasArchivo(incluirInactivos));
    }

    @PostMapping("/reglas-archivo")
    public ResponseEntity<ReglaArchivoRespuesta> crearReglaArchivo(@Valid @RequestBody ReglaArchivoPeticion p) {
        return ResponseEntity.ok(service.crearReglaArchivo(p));
    }

    @PutMapping("/reglas-archivo/{id}")
    public ResponseEntity<ReglaArchivoRespuesta> actualizarReglaArchivo(
            @PathVariable Long id, @Valid @RequestBody ReglaArchivoPeticion p) {
        return ResponseEntity.ok(service.actualizarReglaArchivo(id, p));
    }

    @PatchMapping("/reglas-archivo/{id}/activo")
    public ResponseEntity<ReglaArchivoRespuesta> cambiarActivoReglaArchivo(
            @PathVariable Long id, @Valid @RequestBody ActivoPeticion p) {
        return ResponseEntity.ok(service.cambiarActivoReglaArchivo(id, p.activo()));
    }

    // ---- Configuración institucional ----
    @GetMapping("/configuracion")
    public ResponseEntity<List<ConfiguracionRespuesta>> listarConfiguraciones() {
        return sinCache(service.listarConfiguraciones());
    }

    @PostMapping("/configuracion")
    public ResponseEntity<ConfiguracionRespuesta> crearConfiguracion(@Valid @RequestBody ConfiguracionPeticion p) {
        return ResponseEntity.ok(service.crearConfiguracion(p));
    }

    @PutMapping("/configuracion/{id}")
    public ResponseEntity<ConfiguracionRespuesta> actualizarConfiguracion(
            @PathVariable Long id, @Valid @RequestBody ActualizarConfiguracionPeticion p) {
        return ResponseEntity.ok(service.actualizarConfiguracion(id, p));
    }

    private <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }

    private Pageable paginable(int page, int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return PageRequest.of(Math.max(page, 0), tamano);
    }
}
