package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** HU-039 — Datos del formulario del Libro de Reclamaciones. Todo es obligatorio salvo las imágenes. */
public record PresentarReclamacionRequest(
        @NotBlank @Pattern(regexp = "QUEJA|RECLAMO", message = "Elige si es una queja o un reclamo.") String tipo,
        @NotBlank @Pattern(regexp = "DNI|CE|PASAPORTE|RUC", message = "Elige el tipo de documento.") String tipoDocumento,
        @NotBlank @Size(max = 20) String numeroDocumento,
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(max = 160) String apellidos,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Pattern(regexp = "^[0-9+ ()-]{7,20}$", message = "Ingresa un celular válido.") String celular,
        @NotBlank @Size(min = 5, max = 250) String direccion,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal montoReclamado,
        @NotBlank @Size(max = 300) String descripcionBien,
        @NotBlank @Size(min = 10, max = 4000) String detalle,
        @NotBlank @Size(min = 10, max = 4000) String pedido,
        @AssertTrue(message = "Debes aceptar la declaración jurada.") boolean declaracionJurada,
        @AssertTrue(message = "Debes autorizar la respuesta por correo.") boolean autorizaRespuestaCorreo) {
}
