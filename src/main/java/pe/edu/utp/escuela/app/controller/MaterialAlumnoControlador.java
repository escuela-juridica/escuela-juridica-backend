package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.MaterialAlumnoRespuesta;
import pe.edu.utp.escuela.app.service.MaterialAlumnoServicio;
import pe.edu.utp.escuela.app.service.MaterialAlumnoServicio.ContenidoMaterial;

@Tag(
        name = "Materiales del alumno",
        description = "HU-023 — Consultar materiales protegidos: cada solicitud revalida "
                + "identidad, matrícula, acceso efectivo y pertenencia al curso antes de listar "
                + "o transmitir el contenido de una lección; compartir el enlace de este API no "
                + "le da acceso a quien no está matriculado en el curso.")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class MaterialAlumnoControlador {

    private final MaterialAlumnoServicio servicio;

    @Operation(
            summary = "Listar los materiales de una lección",
            description = "Devuelve los materiales activos de la lección, en orden, con los "
                    + "enlaces para verlos/descargarlos. Para materiales de origen SUBIDO los "
                    + "enlaces apuntan a este mismo API (nunca a la ruta real de "
                    + "almacenamiento); para YOUTUBE/EXTERNO se devuelve el enlace externo real "
                    + "directamente, sin descarga.")
    @ApiResponse(responseCode = "200", description = "Materiales de la lección")
    @ApiResponse(responseCode = "400", description = "Acceso no utilizable: el mensaje explica la causa real")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404",
            description = "No existe la lección, o el alumno no tiene matrícula en su curso")
    @GetMapping("/lecciones/{leccionId}/materiales")
    public List<MaterialAlumnoRespuesta> listar(
            @Parameter(description = "Identificador de la lección", example = "1")
            @PathVariable Long leccionId) {
        return servicio.listar(leccionId);
    }

    @Operation(
            summary = "Ver el contenido de un material",
            description = "Transmite los bytes del archivo (origen SUBIDO) revalidando acceso "
                    + "en esta misma solicitud. Pensado para visualización en línea, p. ej. un "
                    + "visor de PDF embebido.")
    @ApiResponse(responseCode = "200", description = "Contenido del material")
    @ApiResponse(responseCode = "400",
            description = "El material no es de origen SUBIDO, o el acceso no es utilizable")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404",
            description = "No existe el material, o el alumno no tiene matrícula en su curso")
    @GetMapping("/materiales/{id}/contenido")
    public ResponseEntity<Resource> contenido(
            @Parameter(description = "Identificador del material", example = "1")
            @PathVariable Long id) {
        return responder(servicio.contenido(id), ContentDisposition.inline());
    }

    @Operation(
            summary = "Descargar un material",
            description = "Transmite los bytes del archivo (origen SUBIDO) como descarga, "
                    + "revalidando acceso y que el material permita descarga en esta misma "
                    + "solicitud.")
    @ApiResponse(responseCode = "200", description = "Archivo del material")
    @ApiResponse(responseCode = "400",
            description = "El material no permite descarga, no es de origen SUBIDO, o el acceso no es utilizable")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404",
            description = "No existe el material, o el alumno no tiene matrícula en su curso")
    @GetMapping("/materiales/{id}/descargar")
    public ResponseEntity<Resource> descargar(
            @Parameter(description = "Identificador del material", example = "1")
            @PathVariable Long id) {
        return responder(servicio.descargar(id), ContentDisposition.attachment());
    }

    private ResponseEntity<Resource> responder(ContenidoMaterial contenido, ContentDisposition.Builder disposicion) {
        String nombreArchivo = contenido.nombreArchivo() == null ? "archivo" : contenido.nombreArchivo();
        MediaType tipo = contenido.tipoMime() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contenido.tipoMime());
        String disposicionHeader = disposicion.filename(nombreArchivo, StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicionHeader)
                .body(contenido.recurso());
    }
}
