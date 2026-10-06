package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CursoEditorRespuesta(
        Long id,
        String urlAmigable,
        String titulo,
        String descripcion,
        String imagenPortadaUrl,
        Long tipoCursoId,
        String tipoCursoNombre,
        Long categoriaTematicaId,
        String categoriaTematicaNombre,
        Long entidadCertificadoraId,
        String entidadCertificadoraNombre,
        ModalidadCurso modalidad,
        TipoVentaCurso tipoVenta,
        boolean destacado,
        BigDecimal precioRegular,
        BigDecimal precioPromocional,
        Instant promocionInicioEn,
        Instant promocionFinEn,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        LocalDate fechaCierreMatricula,
        Integer cupoMaximo,
        BigDecimal horasAcademicas,
        Integer vigenciaAccesoDias,
        List<String> beneficios,
        String estadoCodigo,
        String estadoNombre,
        boolean publicado,
        List<DocenteCursoRespuesta> docentes,
        List<FirmanteCursoRespuesta> firmantes,
        Instant creadoEn) {
}
