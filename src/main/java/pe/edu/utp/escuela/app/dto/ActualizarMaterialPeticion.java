package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code referencia}/{@code youtubeNoListadoConfirmado}: solo aplican a materiales de origen
 * YOUTUBE o EXTERNO (enlace). Un material SUBIDO no tiene URL que editar aquí; reemplazar su
 * archivo es una funcionalidad aparte, no incluida en esta historia. */
public record ActualizarMaterialPeticion(
        @NotBlank @Size(max = 220) String titulo,
        boolean permiteDescarga,
        @Size(max = 2048) String referencia,
        Boolean youtubeNoListadoConfirmado) {
}
