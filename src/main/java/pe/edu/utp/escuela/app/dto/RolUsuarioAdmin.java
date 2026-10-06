package pe.edu.utp.escuela.app.dto;

public enum RolUsuarioAdmin {
    ALUMNO,
    ADMINISTRADOR;

    public String codigo() {
        return "ROLE_" + name();
    }

    public static RolUsuarioAdmin desdeCodigo(String codigo) {
        return RolUsuarioAdmin.valueOf(codigo.replaceFirst("^ROLE_", ""));
    }
}
