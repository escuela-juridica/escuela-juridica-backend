package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utp.escuela.app.util.NombrePropio;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "persona")
public class Persona extends RegistroAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "persona_id")
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(name = "apellido_paterno", nullable = false, length = 80)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 80)
    private String apellidoMaterno;

    @Column(length = 30)
    private String telefono;

    @Column(name = "documento_identidad", length = 30, unique = true)
    private String documentoIdentidad;

    @Column(name = "foto_url", columnDefinition = "text")
    private String fotoUrl;

    @Column(name = "cargo_profesional", length = 180)
    private String cargoProfesional;

    @Column(name = "biografia_profesional", columnDefinition = "text")
    private String biografiaProfesional;

    @Column(name = "certificado_nombres_pendientes", nullable = false)
    private boolean certificadoNombresPendientes = true;

    @Column(nullable = false)
    private boolean activo = true;

    public void actualizarNombres(String nuevosNombres, String nuevoPaterno, String nuevoMaterno) {
        String nombresFormateados = NombrePropio.formatear(nuevosNombres);
        String paternoFormateado = NombrePropio.formatear(nuevoPaterno);
        String maternoFormateado = NombrePropio.formatear(nuevoMaterno);
        boolean cambio = !igualesSinFormato(nombres, nombresFormateados)
                || !igualesSinFormato(apellidoPaterno, paternoFormateado)
                || !igualesSinFormato(apellidoMaterno, maternoFormateado);
        this.nombres = nombresFormateados;
        this.apellidoPaterno = paternoFormateado;
        this.apellidoMaterno = maternoFormateado;
        if (cambio) {
            certificadoNombresPendientes = true;
        }
    }

    public void confirmarNombres(String nuevosNombres, String nuevoPaterno, String nuevoMaterno) {
        this.nombres = NombrePropio.formatear(nuevosNombres);
        this.apellidoPaterno = NombrePropio.formatear(nuevoPaterno);
        this.apellidoMaterno = NombrePropio.formatear(nuevoMaterno);
        this.certificadoNombresPendientes = false;
    }

    public String nombreCompleto() {
        StringBuilder completo = new StringBuilder(nombres).append(' ').append(apellidoPaterno);
        if (apellidoMaterno != null && !apellidoMaterno.isBlank()) {
            completo.append(' ').append(apellidoMaterno);
        }
        return completo.toString();
    }

    private static boolean igualesSinFormato(String a, String b) {
        return normalizar(a).equals(normalizar(b));
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.strip().replaceAll("\\s+", " ").toUpperCase(Locale.forLanguageTag("es-PE"));
    }
}
