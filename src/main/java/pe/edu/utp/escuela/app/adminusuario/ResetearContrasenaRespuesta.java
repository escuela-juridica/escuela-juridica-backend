package pe.edu.utp.escuela.app.adminusuario;

/** {@code contrasenaTemporal}: solo viene en esta respuesta puntual; no se guarda en texto plano
 * en ningún lado ni se puede volver a consultar después. */
public record ResetearContrasenaRespuesta(
        UsuarioAdminRespuesta usuario,
        String contrasenaTemporal) {
}
