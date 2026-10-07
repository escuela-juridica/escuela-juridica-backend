package pe.edu.utp.escuela.app.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.CancelarMatriculaPeticion;
import pe.edu.utp.escuela.app.dto.CrearMatriculaAdministrativaPeticion;
import pe.edu.utp.escuela.app.dto.MatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import pe.edu.utp.escuela.app.service.MatriculaServicio;

/** Endpoints de matricula de la EP03: gratis, asignacion administrativa y Mis cursos. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatriculaControlador {
    private final MatriculaServicio servicio;

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

    @GetMapping("/admin/matriculas")
    public ResponseEntity<List<MatriculaAdministrativaRespuesta>> listarAdministrativas(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado) {
        return ResponseEntity.ok(servicio.listarAdministrativas(texto, estado));
    }

    @GetMapping("/admin/reportes/matriculas")
    public ResponseEntity<List<ReporteMatriculaRespuesta>> reporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado) {
        return ResponseEntity.ok(servicio.reporte(texto, estado));
    }

    @GetMapping("/admin/reportes/matriculas/exportar")
    public ResponseEntity<byte[]> exportarReporte(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado) {
        StringBuilder csv = new StringBuilder("Alumno;Correo;Curso;Modalidad;Fecha matricula;Fecha activacion;Estado;Origen;Situacion academica\n");
        for (ReporteMatriculaRespuesta fila : servicio.reporte(texto, estado)) {
            csv.append(valorCsv(fila.alumno())).append(';').append(valorCsv(fila.correo())).append(';')
                    .append(valorCsv(fila.curso())).append(';').append(valorCsv(fila.modalidad())).append(';')
                    .append(fila.fechaMatricula()).append(';').append(fila.fechaActivacion()).append(';')
                    .append(fila.estadoMatricula()).append(';').append(fila.formaIngreso()).append(';')
                    .append(fila.situacionAcademica()).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-matriculas.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String valorCsv(String valor) { return "\"" + (valor == null ? "" : valor.replace("\"", "\"\"")) + "\""; }

    @PatchMapping("/admin/matriculas/{matriculaId}/cancelacion")
    public ResponseEntity<MatriculaRespuesta> cancelar(
            @PathVariable Long matriculaId, @Valid @RequestBody CancelarMatriculaPeticion peticion) {
        return ResponseEntity.ok(servicio.cancelar(matriculaId, peticion));
    }
}
