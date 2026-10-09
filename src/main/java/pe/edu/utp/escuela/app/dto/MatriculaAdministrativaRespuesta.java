package pe.edu.utp.escuela.app.dto;

import java.time.Instant;

/** Fila operativa para que Administracion controle matriculas sin exponer credenciales. */
public record MatriculaAdministrativaRespuesta(
        Long id, Long usuarioId, String alumno, String correo, Long cursoId, String cursoTitulo,
        String estado, String formaIngreso, Instant fechaMatricula, Instant fechaVencimiento) {}
