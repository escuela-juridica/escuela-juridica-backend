package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.service.ComprobantePagoService;
import pe.edu.utp.escuela.app.service.ComprobantePagoService.Descarga;

@RestController
@RequestMapping("/api/admin/matriculas/{matriculaId}/comprobante")
@RequiredArgsConstructor
public class ComprobantePagoController {

    private final ComprobantePagoService service;

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Adjuntar (o reemplazar) la boleta o captura de un pago manual")
    public ResponseEntity<Void> adjuntar(@PathVariable Long matriculaId, @RequestParam("archivo") MultipartFile archivo) {
        service.adjuntar(matriculaId, archivo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Descargar el comprobante de un pago manual (privado, solo administración)")
    public ResponseEntity<Resource> descargar(@PathVariable Long matriculaId) {
        Descarga d = service.descargar(matriculaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoMime()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(d.nombre()).build().toString())
                .body(d.archivo());
    }
}
