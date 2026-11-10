package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record OpcionAsistenciaResponse(Long id, String etiqueta, String detalle, Instant fecha) {
}
