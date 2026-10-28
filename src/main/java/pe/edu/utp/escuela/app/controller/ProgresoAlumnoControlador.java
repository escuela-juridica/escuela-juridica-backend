package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.ProgresoVideoPeticion;
import pe.edu.utp.escuela.app.service.ProgresoServicio;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Tag(name = "HU-024 Completar lecciones y registrar progreso",
        description = "Registro de avance de reproducción de video por material y finalización "
                + "de lecciones (automática por umbral, manual, o por asistencia), para alumnos")
public class ProgresoAlumnoControlador {

    private final ProgresoServicio servicio;

    @PostMapping("/materiales/{id}/progreso-video")
    @Operation(summary = "Registrar el avance de reproducción de un video",
            description = "Solo para materiales de video con duración detectable. El porcentaje "
                    + "reconocido nunca retrocede y, al alcanzar el umbral del curso en todos los "
                    + "videos de la lección, la marca como completada automáticamente.")
    @ApiResponse(responseCode = "400", description = "El material no permite registrar progreso de video")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404", description = "El material no existe o está inactivo")
    public ResponseEntity<Void> registrarProgresoVideo(
            @PathVariable Long id, @Valid @RequestBody ProgresoVideoPeticion p) {
        servicio.registrarProgresoVideo(
                id, p.segundosReproducidos(), p.ultimaPosicionSegundos(), p.duracionSegundosTotal());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lecciones/{id}/completar")
    @Operation(summary = "Completar manualmente una lección",
            description = "Para lecciones sin video detectable (check, botón \"Siguiente\" o "
                    + "\"Completar lección\": las tres producen el mismo resultado). En una sesión "
                    + "EN_VIVO sin asistencia PRESENTE/JUSTIFICADA, requiere que ya exista al menos "
                    + "un material (grabación) activo.")
    @ApiResponse(responseCode = "400",
            description = "Se completa automáticamente por video, o la grabación aún no está disponible")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404", description = "La lección no existe")
    public ResponseEntity<Void> completarManual(@PathVariable Long id) {
        servicio.completarManual(id);
        return ResponseEntity.noContent().build();
    }
}
