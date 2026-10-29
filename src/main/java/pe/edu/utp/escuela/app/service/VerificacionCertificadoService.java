package pe.edu.utp.escuela.app.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.VerificacionCertificadoResponse;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;

/**
 * HU-034 — Verificación pública por código o QR, sin sesión. Devuelve una proyección mínima del
 * certificado vigente: nunca el documento de identidad, el correo, el teléfono, la nota, las firmas,
 * el PDF ni el motivo de una anulación. Un código inexistente responde solo «Certificado no
 * encontrado», sin revelar personas.
 */
@Service
@RequiredArgsConstructor
public class VerificacionCertificadoService {

    private static final int LONGITUD_MAXIMA = 80;

    private final CertificadoRepository certificados;

    @Transactional(readOnly = true)
    public VerificacionCertificadoResponse verificar(String codigo) {
        String limpio = codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
        if (limpio.isEmpty() || limpio.length() > LONGITUD_MAXIMA) {
            throw new ResourceNotFoundException("Certificado no encontrado.");
        }
        Certificado c = certificados.findByCodigo(limpio)
                .orElseThrow(() -> new ResourceNotFoundException("Certificado no encontrado."));
        boolean anulado = Certificado.ANULADO.equals(c.getEstado());
        return new VerificacionCertificadoResponse(anulado ? Certificado.ANULADO : Certificado.VIGENTE, c.getCodigo(),
                c.getNombresImpresos(), c.getTituloCursoImpreso(), c.getHorasImpresas(), c.getNivel(),
                c.getEntidadImpresa(), c.getEmitidoEn(), anulado ? c.getAnuladoEn() : null);
    }
}
