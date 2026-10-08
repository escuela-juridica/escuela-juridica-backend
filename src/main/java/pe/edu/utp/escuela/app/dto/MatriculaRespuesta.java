package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Resumen unico que consumen HU-017, HU-019, HU-020 y HU-021. */
public record MatriculaRespuesta(
        Long id, Long cursoId, String cursoTitulo, String cursoUrlAmigable, String imagenPortadaUrl, String modalidad,
        String estado, String formaIngreso, Instant fechaMatricula, Instant fechaActivacion,
        Instant fechaVencimiento, Instant fechaFinalizacion, LocalDate fechaInicio,
        boolean accesoEfectivo, String mensajeAcceso, int porcentajeProgreso,
        int leccionesCompletadas, int totalLecciones, String siguienteLeccion, String estadoNotificacion) {
}
