package pe.edu.utp.escuela.app.dto;

import java.util.List;

/** HU-022 — Módulo del aula, con sus lecciones ya resueltas en estado y orden. */
public record ModuloAulaResponse(
        Long id,
        String titulo,
        int orden,
        List<LeccionAulaResponse> lecciones,
        List<ExamenAulaResponse> examenes) {

    public ModuloAulaResponse {
        lecciones = List.copyOf(lecciones);
        examenes = List.copyOf(examenes);
    }
}
