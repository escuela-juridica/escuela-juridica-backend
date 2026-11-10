package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.time.LocalDate;

public record MatriculaResponse(
        Long id, Long cursoId, String cursoTitulo, String cursoUrlAmigable, String imagenPortadaUrl, String modalidad,
        String estado, String formaIngreso, Instant fechaMatricula, Instant fechaActivacion,
        Instant fechaVencimiento, Instant fechaFinalizacion, LocalDate fechaInicio,
        boolean accesoEfectivo, String mensajeAcceso, int porcentajeProgreso,
        int leccionesCompletadas, int totalLecciones, String siguienteLeccion, String estadoNotificacion,
        boolean cursoCancelado) {
}
