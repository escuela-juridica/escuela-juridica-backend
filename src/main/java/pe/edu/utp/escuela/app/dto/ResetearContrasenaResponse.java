package pe.edu.utp.escuela.app.dto;

public record ResetearContrasenaResponse(
        UsuarioAdminResponse usuario,
        String contrasenaTemporal) {
}
