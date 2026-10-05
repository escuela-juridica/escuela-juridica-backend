package pe.edu.utp.escuela.app.maestra;

public record DocenteRespuesta(
        Long personaId,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String nombreCompleto,
        String fotoUrl,
        String cargoProfesional,
        String biografiaProfesional,
        boolean activo) {
}
