package pe.edu.utp.escuela.app.maestra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un docente es un perfil público: nunca crea cuenta de acceso (ver HU-009). */
public record DocentePeticion(
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        String fotoUrl,
        @NotBlank @Size(max = 180) String cargoProfesional,
        String biografiaProfesional) {
}
