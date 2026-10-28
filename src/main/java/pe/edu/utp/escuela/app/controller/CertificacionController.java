package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.CertificacionResponse;
import pe.edu.utp.escuela.app.dto.ConfirmarDatosRequest;
import pe.edu.utp.escuela.app.service.CertificacionService;
import pe.edu.utp.escuela.app.service.CertificacionService.Descarga;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Tag(name = "HU-032 Obtener mi certificado",
        description = "Situación del certificado de un curso, confirmación de los datos a imprimir, "
                + "emisión inmediata y descarga privada del PDF, para el alumno")
public class CertificacionController {

    private final CertificacionService service;

    @GetMapping("/cursos/{cursoId}/certificacion")
    @Operation(summary = "Consultar la situación de mi certificado en un curso",
            description = "Estados: PENDIENTE_REQUISITOS, PROGRAMADO, PENDIENTE_DATOS, LISTO_PARA_EMITIR, "
                    + "VIGENTE y ANULADO. Solo cuenta las condiciones que el curso exige, con su valor real y su meta. "
                    + "Si ya corresponde emitir sin intervención del alumno, la emisión ocurre aquí.")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404", description = "No hay matrícula del alumno en ese curso")
    public CertificacionResponse consultar(@PathVariable Long cursoId) {
        return service.consultar(cursoId);
    }

    @PutMapping("/cursos/{cursoId}/certificacion/datos")
    @Operation(summary = "Confirmar mis nombres y apellidos para el certificado",
            description = "Los tres campos son obligatorios. Se puede corregir y volver a confirmar mientras el "
                    + "certificado no se haya emitido.")
    @ApiResponse(responseCode = "400", description = "Falta un dato o el certificado ya fue emitido")
    public CertificacionResponse confirmarDatos(@PathVariable Long cursoId,
            @Valid @RequestBody ConfirmarDatosRequest peticion) {
        return service.confirmarDatos(cursoId, peticion);
    }

    @PostMapping("/cursos/{cursoId}/certificacion/emitir")
    @Operation(summary = "Generar mi certificado ahora",
            description = "Solo cuando el curso no tiene espera y el estado es LISTO_PARA_EMITIR. Al emitir se "
                    + "congelan nota, nivel y datos y se cierran los nuevos intentos calificados.")
    @ApiResponse(responseCode = "400", description = "Todavía no se puede emitir o ya fue emitido")
    public CertificacionResponse emitir(@PathVariable Long cursoId) {
        return service.emitirAhora(cursoId);
    }

    @GetMapping("/certificados/{codigo}/descargar")
    @Operation(summary = "Descargar el PDF de un certificado",
            description = "Privado: solo lo descarga su dueño (o un administrador). El PDF nunca se envía por correo.")
    @ApiResponse(responseCode = "404", description = "El certificado no existe o no es tuyo")
    public ResponseEntity<Resource> descargar(@PathVariable String codigo) {
        Descarga descarga = service.descargar(codigo);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(descarga.nombre()).build().toString())
                .body(descarga.archivo());
    }
}
