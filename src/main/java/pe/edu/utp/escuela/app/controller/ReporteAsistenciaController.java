package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.OpcionAsistenciaResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReporteAsistenciaResponse;
import pe.edu.utp.escuela.app.export.ReporteAsistenciaExport;
import pe.edu.utp.escuela.app.service.ReporteAsistenciaService;
import pe.edu.utp.escuela.app.service.ReporteAsistenciaService.Filtro;

@Tag(name = "HU-045 Reporte de asistencia",
        description = "Consulta y exportación de la asistencia a sesiones en vivo (solo cursos "
                + "EN_VIVO e HIBRIDO), para administradores")
@RestController
@RequestMapping("/api/admin/reportes/asistencia")
@RequiredArgsConstructor
public class ReporteAsistenciaController {

    private final ReporteAsistenciaService service;
    private final ReporteAsistenciaExport export;

    @GetMapping
    @Operation(summary = "Consultar el reporte de asistencia (paginado)",
            description = "Un registro por alumno y sesión. El porcentaje acumulado y el cumplimiento "
                    + "usan el mismo denominador que ve el alumno: sesiones no canceladas posteriores "
                    + "a su matrícula.")
    @ApiResponse(responseCode = "400", description = "Curso virtual o filtro no válido")
    public ResponseEntity<PageResponse<ReporteAsistenciaResponse>> reporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(required = false) Long sesionId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "") String asistencia,
            @RequestParam(defaultValue = "") String origen,
            @RequestParam(defaultValue = "") String cumplimiento,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        Page<ReporteAsistenciaResponse> pagina = service.paginado(
                new Filtro(texto, cursoId, sesionId, modalidad, fechaDesde, fechaHasta, asistencia, origen,
                        cumplimiento),
                PageRequest.of(Math.max(page, 0), tamano));
        return ResponseEntity.ok(PageResponse.from(pagina));
    }

    @GetMapping("/exportar-excel")
    @Operation(summary = "Exportar el reporte de asistencia a Excel",
            description = "Conserva los mismos filtros que la consulta y exporta todas las filas que ella devuelve.")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(required = false) Long sesionId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "") String asistencia,
            @RequestParam(defaultValue = "") String origen,
            @RequestParam(defaultValue = "") String cumplimiento) {
        List<ReporteAsistenciaResponse> filas = service.completo(
                new Filtro(texto, cursoId, sesionId, modalidad, fechaDesde, fechaHasta, asistencia, origen,
                        cumplimiento));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-asistencia.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(export.excel(filas));
    }

    @GetMapping("/cursos")
    @Operation(summary = "Cursos que admite el reporte (solo en vivo o híbridos)")
    public ResponseEntity<List<OpcionAsistenciaResponse>> cursos() {
        return ResponseEntity.ok(service.cursosDisponibles());
    }

    @GetMapping("/cursos/{cursoId}/sesiones")
    @Operation(summary = "Sesiones (no canceladas) de un curso en vivo o híbrido, para el filtro")
    @ApiResponse(responseCode = "400", description = "El curso es virtual")
    public ResponseEntity<List<OpcionAsistenciaResponse>> sesiones(@PathVariable Long cursoId) {
        return ResponseEntity.ok(service.sesionesDeCurso(cursoId));
    }
}
