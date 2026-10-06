package pe.edu.utp.escuela.app.dto;

/** {@code reutilizada}: true si el correo ya existía (se conservó identidad y contraseña).
 * {@code contrasenaTemporal}: solo viene con valor cuando la cuenta es nueva de verdad. */
public record CrearUsuarioAdminRespuesta(
        UsuarioAdminRespuesta usuario,
        boolean reutilizada,
        String contrasenaTemporal) {
}
