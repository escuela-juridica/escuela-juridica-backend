package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ReclamacionBandejaResponse;
import pe.edu.utp.escuela.app.dto.ReclamacionDetalleResponse;
import pe.edu.utp.escuela.app.dto.ReenviarRespuestaRequest;
import pe.edu.utp.escuela.app.dto.ResponderReclamacionRequest;
import pe.edu.utp.escuela.app.service.ReclamacionAdminService;
import pe.edu.utp.escuela.app.service.ReclamacionAdminService.Descarga;

@Tag(name = "HU-040 Responder queja o reclamo",
        description = "Bandeja, expediente y respuesta por correo de las quejas y reclamos del Libro de Reclamaciones, "
                + "para administradores. Solo un correo enviado correctamente pasa el caso a RESPONDIDO")
@RestController
@RequestMapping("/api/admin/reclamaciones")
@RequiredArgsConstructor
public class ReclamacionAdminController {

    private final ReclamacionAdminService service;

    @GetMapping
    @Operation(summary = "Bandeja de quejas y reclamos (paginada)",
            description = "Los pendientes van primero, por vencimiento más próximo. Busca por número de caso, documento, "
                    + "nombre o correo. Los próximos a vencer y vencidos se marcan pero siguen abiertos.")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    public ResponseEntity<PageResponse<ReclamacionBandejaResponse>> bandeja(
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(PageResponse.from(
                service.bandeja(estado, tipo, texto, PageRequest.of(Math.max(page, 0), tamano))));
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Cantidad de casos pendientes de respuesta")
    public ResponseEntity<Map<String, Long>> pendientes() {
        return ResponseEntity.ok(Map.of("pendientes", service.pendientes()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Expediente completo: formulario original, imágenes, respuesta e intentos de envío")
    @ApiResponse(responseCode = "404", description = "El caso no existe")
    public ResponseEntity<ReclamacionDetalleResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalle(id));
    }

    @GetMapping("/{id}/adjuntos/{adjuntoId}")
    @Operation(summary = "Descargar una imagen adjunta (privada, solo administración)")
    public ResponseEntity<Resource> adjunto(@PathVariable Long id, @PathVariable Long adjuntoId) {
        Descarga d = service.adjunto(id, adjuntoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoMime()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(d.nombre()).build().toString())
                .body(d.archivo());
    }

    @PostMapping("/{id}/responder")
    @Operation(summary = "Registrar y enviar la respuesta",
            description = "Conserva la respuesta antes de intentar el correo. Si el envío falla, el caso sigue pendiente "
                    + "y la misma respuesta se puede reenviar. La respuesta no se edita.")
    @ApiResponse(responseCode = "409", description = "El caso ya tiene una respuesta registrada")
    public ResponseEntity<ReclamacionDetalleResponse> responder(@PathVariable Long id,
            @Valid @RequestBody ResponderReclamacionRequest peticion) {
        return ResponseEntity.ok(service.responder(id, peticion.respuesta()));
    }

    @PostMapping("/{id}/reenviar")
    @Operation(summary = "Reenviar la misma respuesta",
            description = "Usa el mismo contenido y la fecha original. Con el caso pendiente se puede corregir el correo "
                    + "de destino; cada intento queda registrado.")
    public ResponseEntity<ReclamacionDetalleResponse> reenviar(@PathVariable Long id,
            @Valid @RequestBody ReenviarRespuestaRequest peticion) {
        return ResponseEntity.ok(service.reenviar(id, peticion.correo()));
    }
}
