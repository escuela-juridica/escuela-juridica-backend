package pe.edu.utp.escuela.app.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.utp.escuela.app.service.VerificadorAntiRobot;

@Component
public class VerificadorAntiRobotNativo implements VerificadorAntiRobot {
    private final AntiRobotNativo servicio;
    public VerificadorAntiRobotNativo(AntiRobotNativo servicio) { this.servicio = servicio; }

    @Override public void verificar(String evidencia) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos))
            throw AntiRobotNativo.invalido();
        var request = atributos.getRequest();
        servicio.consumir(VisitanteAntiRobot.leer(request), request.getRemoteAddr(), evidencia);
    }
}
