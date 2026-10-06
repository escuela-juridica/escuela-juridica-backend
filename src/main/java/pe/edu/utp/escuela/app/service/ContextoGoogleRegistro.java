package pe.edu.utp.escuela.app.service;

import java.time.Instant;

/**
 * HU-001 entrega identidad comprobada; HU-002 no inicia OAuth ni valida ID tokens.
 * El adaptador debe comprobar autenticidad e integridad de la referencia y
 * rechazar referencias canceladas, desconocidas o alteradas.
 * Esta interfaz interna no impone formato JWT, proveedor, almacenamiento ni TTL.
 */
public interface ContextoGoogleRegistro {
    Identidad obtenerVerificada(String referencia);

    record Identidad(String subject, String correo, String nombres, String apellidos,
                     String fotoUrl, Instant venceEn) {
        @Override public String toString() { return "IdentidadGoogle[datos omitidos]"; }
    }
}
