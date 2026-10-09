package pe.edu.utp.escuela.app.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.AdvertenciaMatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.CancelarMatriculaPeticion;
import pe.edu.utp.escuela.app.dto.CrearMatriculaAdministrativaPeticion;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaDetalleAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReenvioMatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import pe.edu.utp.escuela.app.export.ReporteMatriculaExportador;
import pe.edu.utp.escuela.app.service.MatriculaServicio;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatriculaControlador {
    private final MatriculaServicio servicio;
    private final ReporteMatriculaExportador exportador;

    @PostMapping("/cursos/{cursoId}/matricula-gratuita")
    public ResponseEntity<MatriculaRespuesta> matricularGratis(@PathVariable Long cursoId) {
        return ResponseEntity.ok(servicio.matricularGratis(cursoId));
    }

    @GetMapping("/app/matriculas")
    public ResponseEntity<List<MatriculaRespuesta>> misCursos() {
        return ResponseEntity.ok(servicio.misCursos());
    }

    @PostMapping("/admin/matriculas")
    public ResponseEntity<MatriculaRespuesta> matricularAdministrativamente(
            @Valid @RequestBody CrearMatriculaAdministrativaPeticion peticion) {
        return ResponseEntity.ok(servicio.matricularAdministrativamente(peticion));
    }

    @GetMapping("/admin/cursos/{cursoId}/matriculas/advertencia-academica")
    public ResponseEntity<AdvertenciaMatriculaRespuesta> advertenciaAcademica(@PathVariable Long cursoId) {
        return ResponseEntity.ok(servicio.consultarAdvertenciaAdministrativa(cursoId));
    }

    @GetMapping("/admin/matriculas/{matriculaId}")
    public ResponseEntity<MatriculaDetalleAdministrativaRespuesta> detalle(@PathVariable Long matriculaId) {
        return ResponseEntity.ok(servicio.detalleAdministrativo(matriculaId));
    }

    @PostMapping("/matriculas/{matriculaId}/reenviar-confirmacion")
    public ResponseEntity<ReenvioMatriculaRespuesta> reenviarConfirmacion(@PathVariable Long matriculaId) {
        return ResponseEntity.ok(servicio.reenviarConfirmacion(matriculaId));
    }

    @GetMapping("/admin/matriculas")
    public ResponseEntity<PageResponse<MatriculaAdministrativaRespuesta>> listarAdministrativas(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        Page<MatriculaAdministrativaRespuesta> pagina = servicio.listarAdministrativas(
                texto, estado, PageRequest.of(Math.max(page, 0), tamano));
        return ResponseEntity.ok(PageResponse.from(pagina));
    }

    @GetMapping("/admin/reportes/matriculas")
    public ResponseEntity<PageResponse<ReporteMatriculaRespuesta>> reporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        Page<ReporteMatriculaRespuesta> pagina = servicio.reportePaginado(texto, estado, cursoId,
                modalidad, fechaDesde, fechaHasta, PageRequest.of(Math.max(page, 0), tamano));
        return ResponseEntity.ok(PageResponse.from(pagina));
    }

    @GetMapping("/admin/reportes/matriculas/exportar")
    public ResponseEntity<byte[]> exportarReporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        byte[] csv = exportador.csv(servicio.reporte(texto, estado, cursoId, modalidad, fechaDesde, fechaHasta));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-matriculas.csv")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)).body(csv);
    }

    @GetMapping("/admin/reportes/matriculas/exportar-excel")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        byte[] excel = exportador.excel(servicio.reporte(texto, estado, cursoId, modalidad, fechaDesde, fechaHasta), texto, estado);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-matriculas.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/admin/reportes/matriculas/exportar-pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        byte[] pdf = exportador.pdf(servicio.reporte(texto, estado, cursoId, modalidad, fechaDesde, fechaHasta), texto, estado);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-matriculas.pdf")
                .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @PatchMapping("/admin/matriculas/{matriculaId}/cancelacion")
    public ResponseEntity<MatriculaRespuesta> cancelar(
            @PathVariable Long matriculaId, @Valid @RequestBody CancelarMatriculaPeticion peticion) {
        return ResponseEntity.ok(servicio.cancelar(matriculaId, peticion));
    }
}
