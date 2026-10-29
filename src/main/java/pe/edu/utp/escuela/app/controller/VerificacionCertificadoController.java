package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.VerificacionCertificadoResponse;
import pe.edu.utp.escuela.app.service.VerificacionCertificadoService;

@Tag(name = "HU-034 Verificar un certificado",
        description = "Consulta pública, sin sesión, de un certificado por su código o QR. Solo datos mínimos: "
                + "nunca documento de identidad, correo, teléfono, nota, firmas ni PDF.")
@RestController
@RequestMapping("/api/publico/certificados")
@RequiredArgsConstructor
public class VerificacionCertificadoController {

    private final VerificacionCertificadoService service;

    @GetMapping("/{codigo}")
    @Operation(summary = "Verificar un certificado por su código",
            description = "VIGENTE devuelve código, titular, curso, horas, nivel, entidad y fecha de emisión. "
                    + "ANULADO informa que no es válido y la fecha de anulación (sin el motivo).")
    @ApiResponse(responseCode = "404", description = "Certificado no encontrado")
    public ResponseEntity<VerificacionCertificadoResponse> verificar(@PathVariable String codigo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.verificar(codigo));
    }
}
