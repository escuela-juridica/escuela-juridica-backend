package pe.edu.utp.escuela.app.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.service.ReferenciaGoogleService;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${esejur.google.client-id:}' != ''")
public class ContextoGoogleRegistroFirmadoIntegration implements ContextoGoogleRegistroIntegration {

    private final ReferenciaGoogleService referencias;

    @Override
    public Identidad obtenerVerificada(String referencia) {
        return referencias.resolver(referencia);
    }
}
