package pe.edu.utp.escuela.app.dto;

public record CrearUsuarioAdminResponse(
        UsuarioAdminResponse usuario,
        boolean reutilizada,
        String contrasenaTemporal) {
}
