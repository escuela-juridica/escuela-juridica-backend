package pe.edu.utp.escuela.app.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import pe.edu.utp.escuela.app.service.MatriculaServicio;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import pe.edu.utp.escuela.app.export.ReporteMatriculaExportador;

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

    // ---------------------------------------------------------------- HU-020 --

    @GetMapping("/admin/matriculas/{matriculaId}")
    public ResponseEntity<MatriculaDetalleAdministrativaRespuesta> detalle(@PathVariable Long matriculaId) {
        return ResponseEntity.ok(servicio.detalleAdministrativo(matriculaId));
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

    @PatchMapping("/admin/matriculas/{matriculaId}/cancelacion")
    public ResponseEntity<MatriculaRespuesta> cancelar(
            @PathVariable Long matriculaId,
            @Valid @RequestBody CancelarMatriculaPeticion peticion) {
        return ResponseEntity.ok(servicio.cancelar(matriculaId, peticion));
    }

    @PostMapping("/matriculas/{matriculaId}/reenviar-confirmacion")
    public ResponseEntity<ReenvioMatriculaRespuesta> reenviarConfirmacion(@PathVariable Long matriculaId) {
        return ResponseEntity.ok(servicio.reenviarConfirmacion(matriculaId));
    }

    // ---------------- HU-041: REPORTE DE MATRÍCULAS ----------------

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

        Page<ReporteMatriculaRespuesta> pagina = servicio.reportePaginado(
                texto, estado, cursoId, modalidad,
                fechaDesde, fechaHasta,
                PageRequest.of(Math.max(page, 0), tamano));

        return ResponseEntity.ok(PageResponse.from(pagina));
    }

    @GetMapping("/admin/reportes/matriculas/exportar/csv")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        List<ReporteMatriculaRespuesta> filas = servicio.reporte(
                texto, estado, cursoId, modalidad, fechaDesde, fechaHasta);

        byte[] archivo = exportador.csv(filas);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-matriculas.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(archivo);
    }

    @GetMapping("/admin/reportes/matriculas/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        List<ReporteMatriculaRespuesta> filas = servicio.reporte(
                texto, estado, cursoId, modalidad, fechaDesde, fechaHasta);

        byte[] archivo = exportador.excel(filas);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-matriculas.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo);
    }

    @GetMapping("/admin/reportes/matriculas/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        List<ReporteMatriculaRespuesta> filas = servicio.reporte(
                texto, estado, cursoId, modalidad, fechaDesde, fechaHasta);

        byte[] archivo = exportador.pdf(filas, texto, estado);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-matriculas.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(archivo);
    }

}
