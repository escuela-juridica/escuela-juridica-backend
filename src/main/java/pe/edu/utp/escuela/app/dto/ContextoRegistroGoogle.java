package pe.edu.utp.escuela.app.dto;
import java.time.Instant;
public record ContextoRegistroGoogle(String correo, String nombres, String apellidos,
                                    String fotoUrl, Instant venceEn) {}
