package pe.edu.utp.escuela.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.dto.ArchivoGuardado;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.PagoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * Boleta o captura opcional que respalda un pago registrado manualmente por la administración. El archivo es
 * privado: no se publica bajo /uploads, solo se descarga por este servicio y únicamente un administrador.
 */
@Service
@RequiredArgsConstructor
public class ComprobantePagoService {

    private final PagoRepository pagos;
    private final ArchivoAlmacenamientoService archivos;
    private final CurrentUserService actual;

    /** Archivo listo para descargar. */
    public record Descarga(Resource archivo, String nombre, String tipoMime) {
    }

    /** Adjunta (o reemplaza) el comprobante del pago manual de la matrícula. */
    @Transactional
    public void adjuntar(Long matriculaId, MultipartFile archivo) {
        exigirAdministrador();
        Pago pago = pagoManual(matriculaId);
        ArchivoGuardado guardado = archivos.guardarComprobantePago(pago.getId(), archivo);
        String anterior = pago.getComprobanteUrl();
        pago.setComprobanteUrl(guardado.referencia());
        pagos.save(pago);
        if (anterior != null) {
            archivos.eliminar(anterior);
        }
    }

    @Transactional(readOnly = true)
    public Descarga descargar(Long matriculaId) {
        exigirAdministrador();
        Pago pago = pagoManual(matriculaId);
        String referencia = pago.getComprobanteUrl();
        if (referencia == null) {
            throw new ResourceNotFoundException("Este pago no tiene comprobante adjunto.");
        }
        String extension = referencia.substring(referencia.lastIndexOf('.') + 1);
        return new Descarga(archivos.cargar(referencia), "comprobante-matricula-" + matriculaId + "." + extension,
                ArchivoAlmacenamientoService.tipoMimeDe(extension));
    }

    private Pago pagoManual(Long matriculaId) {
        return pagos.findByMatricula_IdOrderByResultadoEnDesc(matriculaId).stream()
                .filter(p -> "MANUAL".equals(p.getOrigen()))
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        "Solo los pagos registrados manualmente admiten comprobante."));
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
