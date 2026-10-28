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
import pe.edu.utp.escuela.app.dto.ActivoRequest;
import pe.edu.utp.escuela.app.dto.ActualizarConfiguracionRequest;
import pe.edu.utp.escuela.app.dto.CategoriaRequest;
import pe.edu.utp.escuela.app.dto.CategoriaResponse;
import pe.edu.utp.escuela.app.dto.ConfiguracionRequest;
import pe.edu.utp.escuela.app.dto.ConfiguracionResponse;
import pe.edu.utp.escuela.app.dto.DocenteRequest;
import pe.edu.utp.escuela.app.dto.DocenteResponse;
import pe.edu.utp.escuela.app.dto.EntidadRequest;
import pe.edu.utp.escuela.app.dto.EntidadResponse;
import pe.edu.utp.escuela.app.dto.FirmanteRequest;
import pe.edu.utp.escuela.app.dto.FirmanteResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReglaArchivoRequest;
import pe.edu.utp.escuela.app.dto.ReglaArchivoResponse;
import pe.edu.utp.escuela.app.dto.TipoCursoRequest;
import pe.edu.utp.escuela.app.dto.TipoCursoResponse;
import pe.edu.utp.escuela.app.dto.TipoMaterialRequest;
import pe.edu.utp.escuela.app.dto.TipoMaterialResponse;
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
    public ResponseEntity<PageResponse<TipoCursoResponse>> listarTiposCurso(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarTiposCurso(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/tipos-curso")
    public ResponseEntity<TipoCursoResponse> crearTipoCurso(@Valid @RequestBody TipoCursoRequest p) {
        return ResponseEntity.ok(service.crearTipoCurso(p));
    }

    @PutMapping("/tipos-curso/{id}")
    public ResponseEntity<TipoCursoResponse> actualizarTipoCurso(
            @PathVariable Long id, @Valid @RequestBody TipoCursoRequest p) {
        return ResponseEntity.ok(service.actualizarTipoCurso(id, p));
    }

    @PatchMapping("/tipos-curso/{id}/activo")
    public ResponseEntity<TipoCursoResponse> cambiarActivoTipoCurso(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoTipoCurso(id, p.activo()));
    }

    // ---- Categorías temáticas ----
    @GetMapping("/categorias")
    public ResponseEntity<PageResponse<CategoriaResponse>> listarCategorias(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarCategorias(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/categorias")
    public ResponseEntity<CategoriaResponse> crearCategoria(@Valid @RequestBody CategoriaRequest p) {
        return ResponseEntity.ok(service.crearCategoria(p));
    }

    @PutMapping("/categorias/{id}")
    public ResponseEntity<CategoriaResponse> actualizarCategoria(
            @PathVariable Long id, @Valid @RequestBody CategoriaRequest p) {
        return ResponseEntity.ok(service.actualizarCategoria(id, p));
    }

    @PatchMapping("/categorias/{id}/activo")
    public ResponseEntity<CategoriaResponse> cambiarActivoCategoria(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoCategoria(id, p.activo()));
    }

    // ---- Docentes ----
    @GetMapping("/docentes")
    public ResponseEntity<PageResponse<DocenteResponse>> listarDocentes(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarDocentes(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/docentes")
    public ResponseEntity<DocenteResponse> crearDocente(@Valid @RequestBody DocenteRequest p) {
        return ResponseEntity.ok(service.crearDocente(p));
    }

    @PutMapping("/docentes/{id}")
    public ResponseEntity<DocenteResponse> actualizarDocente(
            @PathVariable Long id, @Valid @RequestBody DocenteRequest p) {
        return ResponseEntity.ok(service.actualizarDocente(id, p));
    }

    @PatchMapping("/docentes/{id}/activo")
    public ResponseEntity<DocenteResponse> cambiarActivoDocente(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoDocente(id, p.activo()));
    }

    // ---- Entidades certificadoras ----
    @GetMapping("/entidades")
    public ResponseEntity<PageResponse<EntidadResponse>> listarEntidades(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarEntidades(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/entidades")
    public ResponseEntity<EntidadResponse> crearEntidad(@Valid @RequestBody EntidadRequest p) {
        return ResponseEntity.ok(service.crearEntidad(p));
    }

    @PutMapping("/entidades/{id}")
    public ResponseEntity<EntidadResponse> actualizarEntidad(
            @PathVariable Long id, @Valid @RequestBody EntidadRequest p) {
        return ResponseEntity.ok(service.actualizarEntidad(id, p));
    }

    @PatchMapping("/entidades/{id}/activo")
    public ResponseEntity<EntidadResponse> cambiarActivoEntidad(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoEntidad(id, p.activo()));
    }

    // ---- Firmantes ----
    @GetMapping("/firmantes")
    public ResponseEntity<PageResponse<FirmanteResponse>> listarFirmantes(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarFirmantes(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/firmantes")
    public ResponseEntity<FirmanteResponse> crearFirmante(@Valid @RequestBody FirmanteRequest p) {
        return ResponseEntity.ok(service.crearFirmante(p));
    }

    @PutMapping("/firmantes/{id}")
    public ResponseEntity<FirmanteResponse> actualizarFirmante(
            @PathVariable Long id, @Valid @RequestBody FirmanteRequest p) {
        return ResponseEntity.ok(service.actualizarFirmante(id, p));
    }

    @PatchMapping("/firmantes/{id}/activo")
    public ResponseEntity<FirmanteResponse> cambiarActivoFirmante(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoFirmante(id, p.activo()));
    }

    // ---- Tipos de material ----
    @GetMapping("/tipos-material")
    public ResponseEntity<PageResponse<TipoMaterialResponse>> listarTiposMaterial(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sinCache(service.listarTiposMaterial(incluirInactivos, paginable(page, size)));
    }

    @PostMapping("/tipos-material")
    public ResponseEntity<TipoMaterialResponse> crearTipoMaterial(@Valid @RequestBody TipoMaterialRequest p) {
        return ResponseEntity.ok(service.crearTipoMaterial(p));
    }

    @PutMapping("/tipos-material/{id}")
    public ResponseEntity<TipoMaterialResponse> actualizarTipoMaterial(
            @PathVariable Long id, @Valid @RequestBody TipoMaterialRequest p) {
        return ResponseEntity.ok(service.actualizarTipoMaterial(id, p));
    }

    @PatchMapping("/tipos-material/{id}/activo")
    public ResponseEntity<TipoMaterialResponse> cambiarActivoTipoMaterial(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoTipoMaterial(id, p.activo()));
    }

    // ---- Reglas de archivo ----
    @GetMapping("/reglas-archivo")
    public ResponseEntity<List<ReglaArchivoResponse>> listarReglasArchivo(
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return sinCache(service.listarReglasArchivo(incluirInactivos));
    }

    @PostMapping("/reglas-archivo")
    public ResponseEntity<ReglaArchivoResponse> crearReglaArchivo(@Valid @RequestBody ReglaArchivoRequest p) {
        return ResponseEntity.ok(service.crearReglaArchivo(p));
    }

    @PutMapping("/reglas-archivo/{id}")
    public ResponseEntity<ReglaArchivoResponse> actualizarReglaArchivo(
            @PathVariable Long id, @Valid @RequestBody ReglaArchivoRequest p) {
        return ResponseEntity.ok(service.actualizarReglaArchivo(id, p));
    }

    @PatchMapping("/reglas-archivo/{id}/activo")
    public ResponseEntity<ReglaArchivoResponse> cambiarActivoReglaArchivo(
            @PathVariable Long id, @Valid @RequestBody ActivoRequest p) {
        return ResponseEntity.ok(service.cambiarActivoReglaArchivo(id, p.activo()));
    }

    // ---- Configuración institucional ----
    @GetMapping("/configuracion")
    public ResponseEntity<List<ConfiguracionResponse>> listarConfiguraciones() {
        return sinCache(service.listarConfiguraciones());
    }

    @PostMapping("/configuracion")
    public ResponseEntity<ConfiguracionResponse> crearConfiguracion(@Valid @RequestBody ConfiguracionRequest p) {
        return ResponseEntity.ok(service.crearConfiguracion(p));
    }

    @PutMapping("/configuracion/{id}")
    public ResponseEntity<ConfiguracionResponse> actualizarConfiguracion(
            @PathVariable Long id, @Valid @RequestBody ActualizarConfiguracionRequest p) {
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
