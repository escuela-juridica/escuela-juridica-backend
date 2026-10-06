package pe.edu.utp.escuela.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.service.AntiRobotNativo;
import pe.edu.utp.escuela.app.service.VisitanteAntiRobot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth/registro/antirobot")
@Tag(name = "HU-002 Anti-robot académico", description = "Desafío propio de demostración; no es protección robusta contra bots")
public class AntiRobotControlador {
    private final AntiRobotNativo servicio;
    public AntiRobotControlador(AntiRobotNativo servicio) { this.servicio = servicio; }

    public record Solucion(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String desafioId,
                           @NotNull @Min(0) @Max(99) Integer respuesta) {
        @Override public String toString() { return "Solucion[omitida]"; }
    }

    @PostMapping("/desafios")
    @Operation(summary = "Solicitar suma con vigencia de dos minutos y cookie de vinculación")
    public ResponseEntity<AntiRobotNativo.Desafio> crear(HttpServletRequest request) {
        String visitante = VisitanteAntiRobot.leer(request);
        if (visitante == null) visitante = servicio.identificador();
        var desafio = servicio.crear(visitante, request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, VisitanteAntiRobot.crear(visitante, request.isSecure()).toString())
                .body(desafio);
    }

    @PostMapping("/verificar")
    @Operation(summary = "Resolver desafío y recibir evidencia temporal de un solo uso")
    public ResponseEntity<AntiRobotNativo.Evidencia> verificar(@Valid @RequestBody Solucion solucion,
                                                             HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.resolver(
                VisitanteAntiRobot.leer(request), request.getRemoteAddr(), solucion.desafioId(), solucion.respuesta()));
    }
}
