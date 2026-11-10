package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

public record MatriculaAdministrativaResponse(
        Long id, Long usuarioId, String alumno, String correo, Long cursoId, String cursoTitulo,
        String estado, String formaIngreso, Instant fechaMatricula, Instant fechaVencimiento) {}
