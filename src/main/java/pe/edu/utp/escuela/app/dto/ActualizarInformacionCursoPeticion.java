package pe.edu.utp.escuela.app.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ActualizarInformacionCursoPeticion(
        @NotBlank @Size(max = 220) String titulo,
        @Size(max = 180) String urlAmigable,
        String descripcion,
        String imagenPortadaUrl,
        Long tipoCursoId,
        Long categoriaTematicaId,
        Long entidadCertificadoraId,
        @NotNull ModalidadCurso modalidad,
        @NotNull TipoVentaCurso tipoVenta,
        boolean destacado,
        @NotNull @Digits(integer = 8, fraction = 2) BigDecimal precioRegular,
        @Digits(integer = 8, fraction = 2) BigDecimal precioPromocional,
        Instant promocionInicioEn,
        Instant promocionFinEn,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        LocalDate fechaCierreMatricula,
        Integer cupoMaximo,
        @Digits(integer = 6, fraction = 2) BigDecimal horasAcademicas,
        Integer vigenciaAccesoDias,
        List<String> beneficios) {
}
