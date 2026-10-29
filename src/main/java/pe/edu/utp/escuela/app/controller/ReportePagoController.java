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
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReportePagoResponse;
import pe.edu.utp.escuela.app.dto.ReportePagoTotalResponse;
import pe.edu.utp.escuela.app.export.ReportePagoExport;
import pe.edu.utp.escuela.app.service.ReportePagoService;
import pe.edu.utp.escuela.app.service.ReportePagoService.Filtro;

@Tag(name = "HU-042 Reporte de pagos",
        description = "Consulta y exportación de los pagos registrados (en línea y administrativos), con total de pagos "
                + "confirmados, para administradores. No es contabilidad ni comprobante SUNAT")
@RestController
@RequestMapping("/api/admin/reportes/pagos")
@RequiredArgsConstructor
public class ReportePagoController {

    private final ReportePagoService service;
    private final ReportePagoExport export;

    @GetMapping
    @Operation(summary = "Consultar los pagos registrados (paginado)",
            description = "Incluye PENDIENTE, APROBADO, RECHAZADO, ERROR, EXPIRADO, REGISTRADO_MANUAL y EXONERADO. "
                    + "El rango de fechas se aplica a la fecha del resultado (Culqi o registro administrativo).")
    @ApiResponse(responseCode = "400", description = "Algún filtro no es válido")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    public ResponseEntity<PageResponse<ReportePagoResponse>> reporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String medio,
            @RequestParam(defaultValue = "") String origen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(PageResponse.from(service.paginado(
                new Filtro(texto, cursoId, estado, medio, origen, fechaDesde, fechaHasta),
                PageRequest.of(Math.max(page, 0), tamano))));
    }

    @GetMapping("/total")
    @Operation(summary = "Total de pagos confirmados dentro de los filtros",
            description = "Suma APROBADO y REGISTRADO_MANUAL con monto mayor que cero. Excluye PENDIENTE, RECHAZADO, "
                    + "ERROR, EXPIRADO y EXONERADO. Una confirmación repetida cuenta una sola vez.")
    public ResponseEntity<ReportePagoTotalResponse> total(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String medio,
            @RequestParam(defaultValue = "") String origen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        return ResponseEntity.ok(service.total(new Filtro(texto, cursoId, estado, medio, origen, fechaDesde, fechaHasta)));
    }

    @GetMapping("/exportar-excel")
    @Operation(summary = "Exportar los pagos registrados a Excel",
            description = "Conserva los mismos filtros que la consulta y cierra con el total de pagos confirmados.")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String medio,
            @RequestParam(defaultValue = "") String origen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        List<ReportePagoResponse> filas = service.completo(
                new Filtro(texto, cursoId, estado, medio, origen, fechaDesde, fechaHasta));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-pagos.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(export.excel(filas));
    }

    @GetMapping("/medios")
    @Operation(summary = "Medios de pago registrados para el filtro")
    public ResponseEntity<List<String>> medios() {
        return ResponseEntity.ok(service.medios());
    }
}
