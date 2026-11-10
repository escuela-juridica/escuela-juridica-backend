package pe.edu.utp.escuela.app.dto;

public record ResultadoAccesoGoogle(String jwt, AccesoGoogleResponse respuesta) {
    @Override public String toString() { return "ResultadoAccesoGoogle[token omitido]"; }
}
