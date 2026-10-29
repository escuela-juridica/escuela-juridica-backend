package pe.edu.utp.escuela.app.dto;

/** CAMBIO_PENDIENTE: falta reemplazar la contraseña temporal. PENDIENTE_VERIFICACION: falta
 * verificar el correo. AMBAS_PENDIENTES: recién creada, faltan las dos. NINGUNA: cuenta operativa. */
public enum CondicionCuentaAdmin {
    NINGUNA,
    PENDIENTE_VERIFICACION,
    CAMBIO_PENDIENTE,
    AMBAS_PENDIENTES;

    public static CondicionCuentaAdmin de(boolean correoVerificado, boolean requiereCambioContrasena) {
        if (!correoVerificado && requiereCambioContrasena) {
            return AMBAS_PENDIENTES;
        }
        if (!correoVerificado) {
            return PENDIENTE_VERIFICACION;
        }
        if (requiereCambioContrasena) {
            return CAMBIO_PENDIENTE;
        }
        return NINGUNA;
    }
}
