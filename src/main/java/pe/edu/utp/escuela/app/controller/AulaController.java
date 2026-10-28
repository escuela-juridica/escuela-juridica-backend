package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.AulaRespuesta;
import pe.edu.utp.escuela.app.service.AulaService;

@Tag(
        name = "Aula del alumno",
        description = "HU-022 — Ingreso y continuidad del curso: temario del aula (módulos y "
                + "lecciones) con el estado de cada elemento y el punto donde continuar, para la "
                + "matrícula del alumno autenticado.")
@RestController
@RequestMapping("/api/aula")
@RequiredArgsConstructor
public class AulaController {

    private final AulaService service;

    @Operation(
            summary = "Consultar el temario del aula de un curso",
            description = "Verifica el acceso efectivo de la matrícula del alumno autenticado al "
                    + "curso y devuelve sus módulos y lecciones con el estado de cada una "
                    + "(completada, disponible o bloqueada), el porcentaje general y la lección "
                    + "donde debe posicionarse \"Continuar\". Si el curso fue cancelado, quien ya "
                    + "tenía acceso conserva la consulta en modo lectura.")
    @ApiResponse(responseCode = "200", description = "Temario del aula")
    @ApiResponse(responseCode = "400", description = "Acceso no utilizable: explica la causa real")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404", description = "No existe matrícula del alumno en ese curso")
    @GetMapping("/cursos/{cursoId}")
    public AulaRespuesta temario(
            @Parameter(description = "Identificador del curso", example = "1")
            @PathVariable Long cursoId) {
        return service.temario(cursoId);
    }
}
