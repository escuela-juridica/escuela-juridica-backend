package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.PanelCursosResponse;
import pe.edu.utp.escuela.app.service.PanelCursosService;

@RestController
@RequestMapping("/api/app/panel")
@RequiredArgsConstructor
@Tag(name = "Panel del alumno")
public class PanelCursosController {

    private final PanelCursosService service;

    @GetMapping("/cursos")
    @Operation(summary = "Listar mis cursos paginados",
            description = "Cursos del alumno autenticado, del más reciente al más antiguo, filtrados por estado "
                    + "(EN_PROGRESO o COMPLETADOS) y por texto en el título. Cada curso indica si su certificado "
                    + "está EMITIDO, ANULADO o SIN_EMITIR. Paginado: 10 por defecto, máximo 50.")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    public PanelCursosResponse cursos(
            @RequestParam(defaultValue = "EN_PROGRESO") String estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        Sort orden = Sort.by(Sort.Direction.DESC, "fechaMatricula").and(Sort.by(Sort.Direction.DESC, "id"));
        return service.listar(estado, q, PageRequest.of(Math.max(page, 0), tamano, orden));
    }
}
