package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.OpcionAsistenciaResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReporteCertificadoResponse;
import pe.edu.utp.escuela.app.export.ReporteCertificadoExport;
import pe.edu.utp.escuela.app.service.ReporteCertificadoService;
import pe.edu.utp.escuela.app.service.ReporteCertificadoService.Filtro;

@Tag(name = "HU-044 Reporte de certificados",
        description = "Consulta y exportación de los certificados emitidos (nivel, vigencia y forma de "
                + "emisión), sin exponer el PDF ni datos privados, para administradores")
@RestController
@RequestMapping("/api/admin/reportes/certificados")
@RequiredArgsConstructor
public class ReporteCertificadoController {

    private final ReporteCertificadoService service;
    private final ReporteCertificadoExport export;

    @GetMapping
    @Operation(summary = "Consultar los certificados emitidos (paginado)",
            description = "Muestra la versión vigente de cada certificado. El rango de fechas se aplica a la "
                    + "fecha de emisión. Formas: AUTOMATICA, SOLICITADA (confirmada por el alumno) y MANUAL.")
    @ApiResponse(responseCode = "400", description = "Algún filtro no es válido")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    public ResponseEntity<PageResponse<ReporteCertificadoResponse>> reporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String nivel,
            @RequestParam(defaultValue = "") String entidad,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String forma,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(PageResponse.from(service.paginado(
                new Filtro(texto, cursoId, nivel, entidad, estado, forma, fechaDesde, fechaHasta),
                PageRequest.of(Math.max(page, 0), tamano))));
    }

    @GetMapping("/exportar-excel")
    @Operation(summary = "Exportar los certificados emitidos a Excel",
            description = "Conserva los mismos filtros que la consulta. No incluye documento de identidad, nota, firmas ni PDF.")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String nivel,
            @RequestParam(defaultValue = "") String entidad,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String forma,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        List<ReporteCertificadoResponse> filas = service.completo(
                new Filtro(texto, cursoId, nivel, entidad, estado, forma, fechaDesde, fechaHasta));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-certificados.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(export.excel(filas));
    }

    @GetMapping("/entidades")
    @Operation(summary = "Entidades certificadoras disponibles para el filtro")
    public ResponseEntity<List<OpcionAsistenciaResponse>> entidades() {
        return ResponseEntity.ok(service.entidades());
    }
}
