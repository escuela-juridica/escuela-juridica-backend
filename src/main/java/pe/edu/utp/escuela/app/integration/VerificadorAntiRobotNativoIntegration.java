package pe.edu.utp.escuela.app.integration;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.utp.escuela.app.util.VisitanteAntiRobot;

@Component
public class VerificadorAntiRobotNativoIntegration implements VerificadorAntiRobotIntegration {
    private final AntiRobotNativoIntegration service;
    public VerificadorAntiRobotNativoIntegration(AntiRobotNativoIntegration service) { this.service = service; }

    @Override public void verificar(String evidencia) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos))
            throw AntiRobotNativoIntegration.invalido();
        var request = atributos.getRequest();
        service.consumir(VisitanteAntiRobot.leer(request), request.getRemoteAddr(), evidencia);
    }
}
