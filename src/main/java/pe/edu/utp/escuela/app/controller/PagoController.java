package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.escuela.app.dto.ConstanciaPagoResponse;
import pe.edu.utp.escuela.app.dto.PagoCheckoutResponse;
import pe.edu.utp.escuela.app.dto.PagoDetalleResponse;
import pe.edu.utp.escuela.app.dto.PagoIniciarRequest;
import pe.edu.utp.escuela.app.dto.PagoSimulacionRequest;
import pe.edu.utp.escuela.app.dto.PagoTokenRequest;
import pe.edu.utp.escuela.app.dto.PagosCursoResponse;
import pe.edu.utp.escuela.app.service.PagoEnLineaService;

/** HU-047 — Pago de un curso con Culqi: iniciar, cobrar el token, ver el resultado y la constancia. */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Tag(name = "HU-047 Pago con Culqi", description = "Matrícula pagada: solo APROBADO activa el acceso")
public class PagoController {

    private final PagoEnLineaService service;

    @PostMapping("/cursos/{cursoId}/pagos/iniciar")
    @Operation(summary = "Confirmar el importe vigente e iniciar un intento de pago",
            description = "No reserva cupo ni concede acceso. Si ya hay un intento PENDIENTE, se reanuda.")
    public PagoCheckoutResponse iniciar(@PathVariable Long cursoId, @Valid @RequestBody PagoIniciarRequest peticion) {
        return service.iniciar(cursoId, peticion.importeConfirmado());
    }

    @GetMapping("/cursos/{cursoId}/pagos")
    @Operation(summary = "Mis intentos de pago de un curso")
    public PagosCursoResponse intentos(@PathVariable Long cursoId) {
        return service.pagosDelCurso(cursoId);
    }

    @PostMapping("/pagos/{numeroPedido}/token")
    @Operation(summary = "Cobrar el token de tarjeta o Yape entregado por el checkout")
    public PagoDetalleResponse token(@PathVariable String numeroPedido, @Valid @RequestBody PagoTokenRequest peticion) {
        return service.procesarToken(numeroPedido, peticion.tokenId());
    }

    @GetMapping("/pagos/{numeroPedido}")
    @Operation(summary = "Estado de un intento de pago")
    public PagoDetalleResponse detalle(@PathVariable String numeroPedido) {
        return service.detalle(numeroPedido);
    }

    @GetMapping("/pagos/{numeroPedido}/constancia")
    @Operation(summary = "Constancia imprimible (solo pagos APROBADOS)")
    public ConstanciaPagoResponse constancia(@PathVariable String numeroPedido) {
        return service.constancia(numeroPedido);
    }

    @PostMapping("/pagos/{numeroPedido}/reenviar-aviso")
    @Operation(summary = "Reenviar el aviso del resultado sin duplicar pago, constancia ni acceso")
    public PagoDetalleResponse reenviarAviso(@PathVariable String numeroPedido) {
        return service.reenviarAviso(numeroPedido);
    }

    @PostMapping("/pagos/{numeroPedido}/simular")
    @Operation(summary = "Solo con Culqi simulado: reproducir el aviso que enviaría Culqi")
    public PagoDetalleResponse simular(@PathVariable String numeroPedido, @Valid @RequestBody PagoSimulacionRequest peticion) {
        return service.simular(numeroPedido, peticion.resultado(), peticion.repetirUltimo());
    }
}
