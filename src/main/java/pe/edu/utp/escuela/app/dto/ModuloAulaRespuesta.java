package pe.edu.utp.escuela.app.dto;

import java.util.List;

/** HU-022 — Módulo del aula, con sus lecciones ya resueltas en estado y orden. */
public record ModuloAulaRespuesta(
        Long id,
        String titulo,
        int orden,
        List<LeccionAulaRespuesta> lecciones,
        List<ExamenAulaRespuesta> examenes) {

    public ModuloAulaRespuesta {
        lecciones = List.copyOf(lecciones);
        examenes = List.copyOf(examenes);
    }
}
