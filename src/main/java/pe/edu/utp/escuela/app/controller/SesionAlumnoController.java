package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.IngresarSesionRespuesta;
import pe.edu.utp.escuela.app.dto.SesionCalendarioRespuesta;
import pe.edu.utp.escuela.app.service.SesionAlumnoService;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Tag(name = "HU-026 Participar en sesiones en vivo",
        description = "Calendario de sesiones, ingreso con registro de asistencia y grabaciones, para alumnos")
public class SesionAlumnoController {

    private final SesionAlumnoService service;

    @GetMapping("/sesiones/calendario")
    @Operation(summary = "Consultar el calendario de sesiones en vivo del alumno",
            description = "Lista las sesiones EN_VIVO de los cursos con matrícula activa dentro del rango. "
                    + "Sin parámetros, usa el mes actual. El enlace real solo viene informado cuando la sesión "
                    + "está EN_VENTANA.")
    public ResponseEntity<List<SesionCalendarioRespuesta>> calendario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.calendario(desde, hasta));
    }

    @PostMapping("/lecciones/{leccionId}/sesion/ingresar")
    @Operation(summary = "Ingresar a una sesión en vivo",
            description = "Dentro de la ventana de inicio a fin, registra la asistencia una sola vez (operación "
                    + "idempotente) y devuelve el enlace real de la reunión.")
    @ApiResponse(responseCode = "400", description = "Fuera de la ventana de la sesión, cancelada o sin acceso vigente")
    public ResponseEntity<IngresarSesionRespuesta> ingresar(@PathVariable Long leccionId) {
        return ResponseEntity.ok(service.ingresar(leccionId));
    }
}
