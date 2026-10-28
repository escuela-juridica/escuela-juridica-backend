package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.ExamenFichaRespuesta;
import pe.edu.utp.escuela.app.dto.GuardarRespuestaPeticion;
import pe.edu.utp.escuela.app.dto.IntentoRespuesta;
import pe.edu.utp.escuela.app.service.ExamenAlumnoService;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Tag(name = "HU-028 / HU-029 Rendir exámenes",
        description = "El alumno consulta un examen, inicia o retoma un intento, guarda sus "
                + "respuestas, las revisa y las envía. El servidor decide el tiempo, la nota y el "
                + "estado del intento.")
public class ExamenAlumnoController {

    private final ExamenAlumnoService service;

    @GetMapping("/examenes/{examenId}")
    @Operation(summary = "Consultar un examen y mis intentos",
            description = "Reglas del examen, intentos usados y restantes, si está habilitado, el "
                    + "historial de intentos y si puedo iniciar uno nuevo (con el motivo si no).")
    @ApiResponse(responseCode = "400", description = "El acceso al curso no está disponible")
    @ApiResponse(responseCode = "403", description = "La cuenta autenticada no tiene rol Alumno")
    @ApiResponse(responseCode = "404", description = "El examen no existe o no hay matrícula en su curso")
    public ExamenFichaRespuesta ficha(@PathVariable Long examenId) {
        return service.ficha(examenId);
    }

    @PostMapping("/examenes/{examenId}/intentos")
    @Operation(summary = "Iniciar o retomar un intento",
            description = "Si ya hay un intento en curso lo devuelve con su tiempo restante; si no, "
                    + "crea uno nuevo siempre que el examen esté habilitado, queden intentos y no "
                    + "haya otro pendiente de revisión.")
    @ApiResponse(responseCode = "400", description = "No se puede iniciar: examen no habilitado, intentos agotados o intento pendiente")
    public ResponseEntity<IntentoRespuesta> iniciar(@PathVariable Long examenId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.iniciar(examenId));
    }

    @GetMapping("/intentos/{intentoId}")
    @Operation(summary = "Consultar un intento",
            description = "Un intento en curso trae sus respuestas guardadas y el tiempo restante; "
                    + "uno ya enviado trae su resultado. Si el tiempo venció, se envía antes de responder.")
    public IntentoRespuesta intento(@PathVariable Long intentoId) {
        return service.intento(intentoId);
    }

    @PutMapping("/intentos/{intentoId}/respuestas/{preguntaId}")
    @Operation(summary = "Guardar la respuesta a una pregunta",
            description = "Reemplaza la respuesta anterior (idempotente). No acepta cambios si el "
                    + "intento ya fue enviado o su tiempo terminó.")
    @ApiResponse(responseCode = "204", description = "Respuesta guardada")
    @ApiResponse(responseCode = "400", description = "Intento cerrado o alternativa no válida")
    public ResponseEntity<Void> guardarRespuesta(@PathVariable Long intentoId, @PathVariable Long preguntaId,
            @Valid @RequestBody GuardarRespuestaPeticion peticion) {
        service.guardarRespuesta(intentoId, preguntaId, peticion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/intentos/{intentoId}/enviar")
    @Operation(summary = "Enviar el intento",
            description = "Califica lo objetivo al instante. Si hay respuestas abiertas, el intento "
                    + "queda PENDIENTE_REVISION con su fecha límite. Enviar dos veces es inofensivo.")
    public IntentoRespuesta enviar(@PathVariable Long intentoId) {
        return service.enviar(intentoId);
    }
}
