package pe.edu.utp.escuela.app.curso;

import java.util.List;

/** Vacío es válido: un curso puede no llevar firmantes todavía (se completa antes de publicar). */
public record AsignarFirmantesPeticion(List<Long> firmanteIds) {
}
