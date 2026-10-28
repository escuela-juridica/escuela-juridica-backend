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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.OpcionAsistenciaRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReporteAcademicoRespuesta;
import pe.edu.utp.escuela.app.export.ReporteAcademicoExportador;
import pe.edu.utp.escuela.app.service.ReporteAcademicoServicio;
import pe.edu.utp.escuela.app.service.ReporteAcademicoServicio.Filtro;

@Tag(name = "HU-043 Reporte académico",
        description = "Consulta y exportación del seguimiento académico por alumno y curso "
                + "(progreso, exámenes, nota, asistencia, revisiones y certificado), para administradores")
@RestController
@RequestMapping("/api/admin/reportes/academico")
@RequiredArgsConstructor
public class ReporteAcademicoControlador {

    private final ReporteAcademicoServicio servicio;
    private final ReporteAcademicoExportador exportador;

    @GetMapping
    @Operation(summary = "Consultar el reporte académico (paginado)",
            description = "Una fila por matrícula. Muestra solo las condiciones que el curso tiene "
                    + "activas, con su valor real y su meta; el rango de fechas se aplica a la fecha "
                    + "de matrícula.")
    @ApiResponse(responseCode = "400", description = "Algún filtro no es válido")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    public ResponseEntity<PageResponse<ReporteAcademicoRespuesta>> reporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "") String situacion,
            @RequestParam(defaultValue = "") String cumplimiento,
            @RequestParam(defaultValue = "") String certificado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        Page<ReporteAcademicoRespuesta> pagina = servicio.paginado(
                new Filtro(texto, cursoId, modalidad, fechaDesde, fechaHasta, situacion, cumplimiento, certificado),
                PageRequest.of(Math.max(page, 0), tamano));
        return ResponseEntity.ok(PageResponse.from(pagina));
    }

    @GetMapping("/exportar-excel")
    @Operation(summary = "Exportar el reporte académico a Excel",
            description = "Conserva los mismos filtros que la consulta y exporta todas las filas que ella devuelve.")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "") String situacion,
            @RequestParam(defaultValue = "") String cumplimiento,
            @RequestParam(defaultValue = "") String certificado) {
        List<ReporteAcademicoRespuesta> filas = servicio.completo(
                new Filtro(texto, cursoId, modalidad, fechaDesde, fechaHasta, situacion, cumplimiento, certificado));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-academico.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.excel(filas));
    }

    @GetMapping("/cursos")
    @Operation(summary = "Cursos disponibles para el filtro")
    public ResponseEntity<List<OpcionAsistenciaRespuesta>> cursos() {
        return ResponseEntity.ok(servicio.cursosDisponibles());
    }
}
