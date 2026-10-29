package pe.edu.utp.escuela.app.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.AdvertenciaMatriculaResponse;
import pe.edu.utp.escuela.app.dto.CancelarMatriculaRequest;
import pe.edu.utp.escuela.app.dto.CrearMatriculaAdministrativaRequest;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaResponse;
import pe.edu.utp.escuela.app.dto.MatriculaDetalleAdministrativaResponse;
import pe.edu.utp.escuela.app.dto.MatriculaResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReenvioMatriculaResponse;
import pe.edu.utp.escuela.app.service.MatriculaService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import pe.edu.utp.escuela.app.export.ReporteMatriculaExport;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatriculaController {
    private final MatriculaService service;
    private final ReporteMatriculaExport export;

    @PostMapping("/cursos/{cursoId}/matricula-gratuita")
    public ResponseEntity<MatriculaResponse> matricularGratis(@PathVariable Long cursoId) {
        return ResponseEntity.ok(service.matricularGratis(cursoId));
    }

    @GetMapping("/app/matriculas")
    public ResponseEntity<List<MatriculaResponse>> misCursos() {
        return ResponseEntity.ok(service.misCursos());
    }

    @PostMapping("/admin/matriculas")
    public ResponseEntity<MatriculaResponse> matricularAdministrativamente(
            @Valid @RequestBody CrearMatriculaAdministrativaRequest peticion) {
        return ResponseEntity.ok(service.matricularAdministrativamente(peticion));
    }

    @GetMapping("/admin/cursos/{cursoId}/matriculas/advertencia-academica")
    public ResponseEntity<AdvertenciaMatriculaResponse> advertenciaAcademica(@PathVariable Long cursoId) {
        return ResponseEntity.ok(service.consultarAdvertenciaAdministrativa(cursoId));
    }

    // ---------------------------------------------------------------- HU-020 --

    @GetMapping("/admin/matriculas/{matriculaId}")
    public ResponseEntity<MatriculaDetalleAdministrativaResponse> detalle(@PathVariable Long matriculaId) {
        return ResponseEntity.ok(service.detalleAdministrativo(matriculaId));
    }

    @GetMapping("/admin/matriculas")
    public ResponseEntity<PageResponse<MatriculaAdministrativaResponse>> listarAdministrativas(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        Page<MatriculaAdministrativaResponse> pagina = service.listarAdministrativas(
                texto, estado, PageRequest.of(Math.max(page, 0), tamano));
        return ResponseEntity.ok(PageResponse.from(pagina));
    }

    @PatchMapping("/admin/matriculas/{matriculaId}/cancelacion")
    public ResponseEntity<MatriculaResponse> cancelar(
            @PathVariable Long matriculaId,
            @Valid @RequestBody CancelarMatriculaRequest peticion) {
        return ResponseEntity.ok(service.cancelar(matriculaId, peticion));
    }

    @PostMapping("/matriculas/{matriculaId}/reenviar-confirmacion")
    public ResponseEntity<ReenvioMatriculaResponse> reenviarConfirmacion(@PathVariable Long matriculaId) {
        return ResponseEntity.ok(service.reenviarConfirmacion(matriculaId));
    }

    // ---------------- HU-041: REPORTE DE MATRÍCULAS ----------------

    @GetMapping("/admin/reportes/matriculas")
    public ResponseEntity<PageResponse<ReporteMatriculaResponse>> reporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int tamano = Math.min(Math.max(size, 1), 50);

        Page<ReporteMatriculaResponse> pagina = service.reportePaginado(
                texto, estado, cursoId, modalidad,
                fechaDesde, fechaHasta,
                PageRequest.of(Math.max(page, 0), tamano));

        return ResponseEntity.ok(PageResponse.from(pagina));
    }

    @GetMapping("/admin/reportes/matriculas/exportar")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        List<ReporteMatriculaResponse> filas = service.reporte(
                texto, estado, cursoId, modalidad, fechaDesde, fechaHasta);

        byte[] archivo = export.csv(filas);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-matriculas.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(archivo);
    }

    @GetMapping("/admin/reportes/matriculas/exportar-excel")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        List<ReporteMatriculaResponse> filas = service.reporte(
                texto, estado, cursoId, modalidad, fechaDesde, fechaHasta);

        byte[] archivo = export.excel(filas);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-matriculas.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo);
    }

    @GetMapping("/admin/reportes/matriculas/exportar-pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        List<ReporteMatriculaResponse> filas = service.reporte(
                texto, estado, cursoId, modalidad, fechaDesde, fechaHasta);

        byte[] archivo = export.pdf(filas, texto, estado);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-matriculas.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(archivo);
    }

}
