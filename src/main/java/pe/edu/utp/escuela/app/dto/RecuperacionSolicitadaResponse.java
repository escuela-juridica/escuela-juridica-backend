package pe.edu.utp.escuela.app.dto;

public record RecuperacionSolicitadaResponse(String mensaje) {

    private static final String MENSAJE_NEUTRAL =
            "Si existe una cuenta asociada a ese correo, recibirás un enlace de recuperación.";

    public static RecuperacionSolicitadaResponse neutral() {
        return new RecuperacionSolicitadaResponse(MENSAJE_NEUTRAL);
    }
}
