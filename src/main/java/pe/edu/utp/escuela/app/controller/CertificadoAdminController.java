package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import pe.edu.utp.escuela.app.dto.CertificadoGestionResponse;
import pe.edu.utp.escuela.app.dto.EmisionManualSituacionResponse;
import pe.edu.utp.escuela.app.dto.EmitirCertificadoManualRequest;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.service.CertificadoAdminService;

/** HU-035/HU-036 — Gestión administrativa de certificados (solo administrador). */
@RestController
@RequestMapping("/api/admin/certificados")
@RequiredArgsConstructor
@Tag(name = "HU-035 Emisión manual", description = "Gestión administrativa de certificados")
public class CertificadoAdminController {

    private final CertificadoAdminService service;

    @GetMapping("/gestion")
    @Operation(summary = "Matrículas con la situación de su certificado")
    public ResponseEntity<PageResponse<CertificadoGestionResponse>> listar(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(PageResponse.from(service.listar(texto, estado, PageRequest.of(Math.max(page, 0), tamano))));
    }

    @GetMapping("/manual/{matriculaId}")
    @Operation(summary = "Situación de un alumno antes de emitir manualmente")
    public EmisionManualSituacionResponse situacion(@PathVariable Long matriculaId) {
        return service.situacion(matriculaId);
    }

    @PostMapping("/manual/{matriculaId}")
    @Operation(summary = "Emitir excepcionalmente un certificado con motivo")
    public EmisionManualSituacionResponse emitir(@PathVariable Long matriculaId,
            @Valid @RequestBody EmitirCertificadoManualRequest peticion) {
        return service.emitir(matriculaId, peticion);
    }
}
