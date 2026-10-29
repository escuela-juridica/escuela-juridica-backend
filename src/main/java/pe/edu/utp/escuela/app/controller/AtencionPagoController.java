package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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
import pe.edu.utp.escuela.app.dto.AtencionPagoResponse;
import pe.edu.utp.escuela.app.dto.AtenderAtencionPagoRequest;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.service.AtencionPagoService;

@Tag(name = "HU-037 Excepciones de matrículas y pagos",
        description = "Alertas de sobrecupo y de pagos aprobados tarde o tras una cancelación, y registro de la atención "
                + "brindada por la Escuela fuera de ESEJUR. Nunca cambia el resultado de Culqi ni devuelve dinero")
@RestController
@RequestMapping("/api/admin/atenciones-pago")
@RequiredArgsConstructor
public class AtencionPagoController {

    private final AtencionPagoService service;

    @GetMapping
    @Operation(summary = "Bandeja de excepciones (paginada, las pendientes primero)")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    public ResponseEntity<PageResponse<AtencionPagoResponse>> listar(
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(PageResponse.from(
                service.listar(estado, tipo, texto, PageRequest.of(Math.max(page, 0), tamano))));
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Cantidad de excepciones pendientes de atención")
    public ResponseEntity<Map<String, Long>> pendientes() {
        return ResponseEntity.ok(Map.of("pendientes", service.pendientes()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una excepción: pago, matrícula, acceso y cupo por separado")
    @ApiResponse(responseCode = "404", description = "La excepción no existe")
    public ResponseEntity<AtencionPagoResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalle(id));
    }

    @PostMapping("/{id}/atender")
    @Operation(summary = "Registrar la atención brindada",
            description = "Conserva el detalle, la fecha y el responsable. No modifica el pago, la matrícula ni el acceso.")
    @ApiResponse(responseCode = "409", description = "La excepción ya fue atendida")
    public ResponseEntity<AtencionPagoResponse> atender(@PathVariable Long id,
            @Valid @RequestBody AtenderAtencionPagoRequest peticion) {
        return ResponseEntity.ok(service.atender(id, peticion.resultado()));
    }
}
