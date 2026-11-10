package pe.edu.utp.escuela.app.dto;

public record AccesoGoogleResponse(String estado, SesionAccesoResponse sesion, String referenciaRegistro) {

    public static final String SESION = "SESION";
    public static final String REGISTRO = "REGISTRO";

    public static AccesoGoogleResponse conSesion(SesionAccesoResponse sesion) {
        return new AccesoGoogleResponse(SESION, sesion, null);
    }

    public static AccesoGoogleResponse paraRegistro(String referencia) {
        return new AccesoGoogleResponse(REGISTRO, null, referencia);
    }

    @Override public String toString() { return "AccesoGoogleResponse[" + estado + "]"; }
}
