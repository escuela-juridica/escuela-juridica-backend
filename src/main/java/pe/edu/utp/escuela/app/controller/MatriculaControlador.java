package pe.edu.utp.escuela.app.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.AdvertenciaMatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.CrearMatriculaAdministrativaPeticion;
import pe.edu.utp.escuela.app.dto.MatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.ReenvioMatriculaRespuesta;
import pe.edu.utp.escuela.app.service.MatriculaServicio;

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

    @GetMapping("/admin/cursos/{cursoId}/matriculas/advertencia-academica")
    public ResponseEntity<AdvertenciaMatriculaRespuesta> advertenciaAcademica(@PathVariable Long cursoId) {
        return ResponseEntity.ok(servicio.consultarAdvertenciaAdministrativa(cursoId));
    }

    // HU-020 (detalle, listarAdministrativas, cancelar) se retiró deliberadamente — ver
    // docs/epica-4/HU-020-MAPA-TECNICO-CONTROL-MATRICULAS.md para reconstruirla.

    @PostMapping("/matriculas/{matriculaId}/reenviar-confirmacion")
    public ResponseEntity<ReenvioMatriculaRespuesta> reenviarConfirmacion(@PathVariable Long matriculaId) {
        return ResponseEntity.ok(servicio.reenviarConfirmacion(matriculaId));
    }

    // HU-041 (reporte, exportarReporte, exportarExcel, exportarPdf, y el campo `exportador`) se
    // retiró deliberadamente — ver docs/epica-4/HU-041-MAPA-TECNICO-REPORTE-MATRICULAS.md para
    // reconstruirla.

}
