package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.DashboardResponse;
import pe.edu.utp.escuela.app.service.DashboardService;

@Tag(name = "HU-046 Dashboard administrativo",
        description = "Cuatro gráficos de matrículas y pagos con los mismos filtros y reglas de conteo que los reportes. "
                + "No muestra tareas, alertas ni datos personales")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    @Operation(summary = "Datos de los cuatro gráficos",
            description = "Sin fechas usa el año calendario actual. Matrículas por fecha de activación; pagos confirmados "
                    + "(APROBADO y REGISTRADO_MANUAL con monto mayor que cero) por fecha del resultado.")
    @ApiResponse(responseCode = "400", description = "Rango o modalidad no válidos")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    public ResponseEntity<DashboardResponse> consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String modalidad) {
        return ResponseEntity.ok(service.consultar(desde, hasta, cursoId, modalidad));
    }
}
