package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.dto.PresentarReclamacionRequest;
import pe.edu.utp.escuela.app.dto.ReclamacionConstanciaResponse;
import pe.edu.utp.escuela.app.dto.ReclamacionPrecargaResponse;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.ReclamacionService;

/** HU-039 — Libro de Reclamaciones: se presenta con o sin sesión; con sesión el caso queda ligado a la cuenta. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "HU-039 Libro de reclamaciones", description = "Presentar una queja o reclamo y recibir su constancia")
public class ReclamacionController {

    private final ReclamacionService service;
    private final CurrentUserService actual;

    @PostMapping(value = "/publico/reclamaciones", consumes = "multipart/form-data")
    @Operation(summary = "Presentar una queja o reclamo sin iniciar sesión")
    public ResponseEntity<ReclamacionConstanciaResponse> presentarPublico(
            @RequestPart("datos") @Valid PresentarReclamacionRequest datos,
            @RequestPart(value = "imagenes", required = false) List<MultipartFile> imagenes) {
        return ResponseEntity.ok(service.presentar(null, datos, imagenes));
    }

    @PostMapping(value = "/app/reclamaciones", consumes = "multipart/form-data")
    @Operation(summary = "Presentar una queja o reclamo con sesión; queda ligado a la cuenta")
    public ResponseEntity<ReclamacionConstanciaResponse> presentarConSesion(
            @RequestPart("datos") @Valid PresentarReclamacionRequest datos,
            @RequestPart(value = "imagenes", required = false) List<MultipartFile> imagenes) {
        return ResponseEntity.ok(service.presentar(actual.get().userId(), datos, imagenes));
    }

    @GetMapping("/app/reclamaciones/precarga")
    @Operation(summary = "Datos conocidos de la persona con sesión para prellenar el formulario")
    public ReclamacionPrecargaResponse precarga() {
        return service.precarga(actual.get().userId());
    }
}
