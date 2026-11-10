package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record InstruccionesPagoResponse(String qrUrl, String enlace, String codigo, Instant venceEn) {
}
