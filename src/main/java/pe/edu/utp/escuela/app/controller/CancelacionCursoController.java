package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.CancelacionAfectadoResponse;
import pe.edu.utp.escuela.app.dto.CancelacionCursoResponse;
import pe.edu.utp.escuela.app.dto.CancelarCursoRequest;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.service.CancelacionCursoService;

@Tag(name = "HU-038 Cancelar curso",
        description = "Cancelación completa de un curso con motivo: detiene ventas, cancela sesiones futuras, conserva "
                + "evidencia y envía un solo aviso por alumno afectado. ESEJUR no devuelve dinero")
@RestController
@RequestMapping("/api/admin/cursos/{cursoId}/cancelacion")
@RequiredArgsConstructor
public class CancelacionCursoController {

    private final CancelacionCursoService service;

    @GetMapping
    @Operation(summary = "Impacto de la cancelación y estado de los avisos",
            description = "Alumnos con acceso (finalizados y sin finalizar), pagos en proceso, sesiones futuras y avisos.")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    @ApiResponse(responseCode = "404", description = "El curso no existe")
    public ResponseEntity<CancelacionCursoResponse> impacto(@PathVariable Long cursoId) {
        return ResponseEntity.ok(service.impacto(cursoId));
    }

    @PostMapping("/cancelar")
    @Operation(summary = "Cancelar el curso por completo",
            description = "Solo PUBLICADO o EN_CURSO. Exige motivo y confirmación. Un fallo de correo no revierte la cancelación.")
    @ApiResponse(responseCode = "400", description = "Estado no cancelable, motivo o confirmación inválidos")
    public ResponseEntity<CancelacionCursoResponse> cancelar(@PathVariable Long cursoId,
            @Valid @RequestBody CancelarCursoRequest peticion) {
        return ResponseEntity.ok(service.cancelar(cursoId, peticion.motivo()));
    }

    @GetMapping("/afectados")
    @Operation(summary = "Alumnos afectados y su aviso (paginado, los fallidos primero)")
    public ResponseEntity<PageResponse<CancelacionAfectadoResponse>> afectados(@PathVariable Long cursoId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(PageResponse.from(service.afectados(cursoId, PageRequest.of(Math.max(page, 0), tamano))));
    }

    @PostMapping("/reenviar-avisos")
    @Operation(summary = "Reintentar los avisos fallidos o pendientes",
            description = "No duplica los avisos ya enviados.")
    public ResponseEntity<CancelacionCursoResponse> reenviarAvisos(@PathVariable Long cursoId) {
        return ResponseEntity.ok(service.reenviarAvisos(cursoId));
    }
}
