package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import pe.edu.utp.escuela.app.dto.BandejaResumenResponse;
import pe.edu.utp.escuela.app.dto.CalificarIntentoRequest;
import pe.edu.utp.escuela.app.dto.IntentoPendienteResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.RevisionIntentoResponse;
import pe.edu.utp.escuela.app.service.RevisionExamenService;
import pe.edu.utp.escuela.app.service.RevisionExamenService.Filtro;

@RestController
@RequestMapping("/api/admin/evaluaciones")
@RequiredArgsConstructor
@Tag(name = "HU-030 Calificar respuestas abiertas",
        description = "Bandeja de intentos pendientes de revisión, revisión de las respuestas abiertas, "
                + "publicación de la calificación y aviso al alumno, para administradores")
public class RevisionExamenController {

    private final RevisionExamenService service;

    @GetMapping("/pendientes")
    @Operation(summary = "Bandeja de intentos pendientes de revisión (paginada)",
            description = "Un intento por fila, ordenado por la fecha límite más cercana. El plazo "
                    + "(VENCIDO, PROXIMO, EN_PLAZO) permite atender primero lo urgente.")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no es administradora")
    public ResponseEntity<PageResponse<IntentoPendienteResponse>> pendientes(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(defaultValue = "") String plazo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(PageResponse.from(
                service.bandeja(new Filtro(texto, cursoId, plazo), PageRequest.of(Math.max(page, 0), tamano))));
    }

    @GetMapping("/pendientes/resumen")
    @Operation(summary = "Totales de la bandeja: pendientes, próximos a vencer y vencidos")
    public BandejaResumenResponse resumen() {
        return service.resumen();
    }

    @GetMapping("/intentos/{intentoId}")
    @Operation(summary = "Abrir un intento para revisarlo",
            description = "Trae las respuestas abiertas completas con el puntaje máximo de cada pregunta. "
                    + "Un intento ya calificado se devuelve en solo lectura.")
    @ApiResponse(responseCode = "404", description = "El intento no existe")
    public RevisionIntentoResponse detalle(@PathVariable Long intentoId) {
        return service.detalle(intentoId);
    }

    @PostMapping("/intentos/{intentoId}/calificar")
    @Operation(summary = "Publicar la calificación de un intento",
            description = "Exige un puntaje por cada respuesta abierta pendiente, entre 0 y el máximo de su "
                    + "pregunta. Recalcula la nota, pasa el intento a CALIFICADO y avisa al alumno por correo "
                    + "(un fallo del correo no revierte la nota).")
    @ApiResponse(responseCode = "400", description = "Faltan respuestas, puntaje fuera de rango o intento ya calificado")
    public RevisionIntentoResponse calificar(@PathVariable Long intentoId,
            @Valid @RequestBody CalificarIntentoRequest peticion) {
        return service.calificar(intentoId, peticion);
    }

    @PostMapping("/intentos/{intentoId}/reenviar-aviso")
    @Operation(summary = "Reenviar el aviso «Tu examen fue calificado»")
    @ApiResponse(responseCode = "204", description = "Aviso pedido")
    public ResponseEntity<Void> reenviarAviso(@PathVariable Long intentoId) {
        service.reenviarAviso(intentoId);
        return ResponseEntity.noContent().build();
    }
}
