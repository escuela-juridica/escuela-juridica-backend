package pe.edu.utp.escuela.app.dto;

import java.util.List;

/** HU-025 — Bloque "Tu avance hacia el certificado": solo las condiciones activas del curso, y una
 * frase que dice qué falta. */
public record AvanceCertificadoRespuesta(
        List<CondicionAvanceRespuesta> condiciones,
        boolean cumpleTodo,
        String fraseAccionable) {

    public AvanceCertificadoRespuesta {
        condiciones = List.copyOf(condiciones);
    }
}
