package pe.edu.utp.escuela.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CertificacionResponse(
        Long cursoId,
        String curso,
        String estado,
        String mensaje,
        List<CondicionAvanceResponse> condiciones,
        String fraseAccionable,
        boolean exigeExamenes,
        BigDecimal notaFinal,
        String nivelProyectado,
        String entidadProyectada,
        Instant fechaFinalizacion,
        LocalDate fechaBaseEmision,
        Instant fechaEmisionProgramada,
        int diasEspera,
        boolean emisionAutomatica,
        DatosCertificado datos,
        boolean puedeConfirmarDatos,
        boolean puedeGenerarAhora,
        CertificadoEmitido certificado) {

    public CertificacionResponse {
        condiciones = List.copyOf(condiciones);
    }

    public record DatosCertificado(String nombres, String apellidoPaterno, String apellidoMaterno,
            boolean confirmados, Instant confirmadosEn) {
    }

    public record CertificadoEmitido(String codigo, String estado, String nivel, BigDecimal nota, Instant emitidoEn,
            String nombresImpresos, String entidad, String descargaUrl) {
    }
}
