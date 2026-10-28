package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.CorregirAsistenciaPeticion;
import pe.edu.utp.escuela.app.dto.OtorgarIntentoPeticion;
import pe.edu.utp.escuela.app.dto.SeguimientoAcademicoRespuesta;
import pe.edu.utp.escuela.app.service.SeguimientoAcademicoService;

@RestController
@RequestMapping("/api/admin/matriculas/{matriculaId}/academico")
@RequiredArgsConstructor
@Tag(name = "HU-031 Excepciones académicas",
        description = "Consulta integral del desempeño de un alumno y excepciones justificadas "
                + "(corregir una asistencia, conceder un intento adicional) sin alterar el historial.")
public class SeguimientoAcademicoController {

    private final SeguimientoAcademicoService service;

    @GetMapping
    @Operation(summary = "Consultar el desempeño de un alumno en su curso",
            description = "Solo lectura: avance por lección, intentos y notas, promedio, asistencia, "
                    + "condiciones del curso, certificado y el historial de excepciones.")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    @ApiResponse(responseCode = "404", description = "La matrícula no existe")
    public SeguimientoAcademicoRespuesta consultar(@PathVariable Long matriculaId) {
        return service.consultar(matriculaId);
    }

    @PostMapping("/intentos-adicionales")
    @Operation(summary = "Conceder un intento adicional",
            description = "Solo en un examen calificado cuyos intentos se agotaron sin aprobar y "
                    + "antes de emitir el certificado. Agrega exactamente un intento y conserva el anterior.")
    @ApiResponse(responseCode = "204", description = "Intento adicional concedido")
    @ApiResponse(responseCode = "400", description = "El examen no admite un intento adicional ahora")
    public ResponseEntity<Void> otorgarIntento(@PathVariable Long matriculaId,
            @Valid @RequestBody OtorgarIntentoPeticion peticion) {
        service.otorgarIntento(matriculaId, peticion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/asistencias/{asistenciaId}/correccion")
    @Operation(summary = "Corregir la asistencia de una sesión",
            description = "Cambia el estado de UNA asistencia con motivo, conservando el valor anterior "
                    + "en el historial, y recalcula el progreso y la asistencia acumulada.")
    @ApiResponse(responseCode = "204", description = "Asistencia corregida")
    @ApiResponse(responseCode = "400", description = "Sesión no realizada o estado no válido")
    public ResponseEntity<Void> corregirAsistencia(@PathVariable Long matriculaId, @PathVariable Long asistenciaId,
            @Valid @RequestBody CorregirAsistenciaPeticion peticion) {
        service.corregirAsistencia(matriculaId, asistenciaId, peticion);
        return ResponseEntity.noContent().build();
    }
}
