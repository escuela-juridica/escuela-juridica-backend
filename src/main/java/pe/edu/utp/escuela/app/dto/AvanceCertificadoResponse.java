package pe.edu.utp.escuela.app.dto;

import java.util.List;

public record AvanceCertificadoResponse(
        List<CondicionAvanceResponse> condiciones,
        boolean cumpleTodo,
        String fraseAccionable) {

    public AvanceCertificadoResponse {
        condiciones = List.copyOf(condiciones);
    }
}
