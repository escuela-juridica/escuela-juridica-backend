package pe.edu.utp.escuela.app.dto;

import java.time.Instant;
import java.util.List;

public record MatriculaDetalleAdministrativaResponse(
        Long id, Long usuarioId, String alumno, String correo, Long cursoId, String cursoTitulo,
        String modalidad, String estado, String formaIngreso, Instant fechaMatricula,
        Instant fechaActivacion, Instant fechaVencimiento, Instant fechaFinalizacion,
        String motivoCancelacion, Long creadoPorUsuarioId, String responsable,
        List<PagoMatriculaDetalleResponse> pagos,
        List<HistorialEstadoMatriculaResponse> historialEstados) {}
