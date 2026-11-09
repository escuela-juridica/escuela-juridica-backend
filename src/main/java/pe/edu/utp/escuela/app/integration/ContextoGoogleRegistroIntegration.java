package pe.edu.utp.escuela.app.integration;

import java.time.Instant;

public interface ContextoGoogleRegistroIntegration {
    Identidad obtenerVerificada(String referencia);

    record Identidad(String subject, String correo, String nombres, String apellidos,
                     String fotoUrl, Instant venceEn) {
        @Override public String toString() { return "IdentidadGoogle[datos omitidos]"; }
    }
}
