package pe.edu.utp.escuela.app.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.MiCertificadoResponse;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-033 — Mis certificados: lista únicamente los del alumno autenticado, con lo impreso en cada uno
 * (la versión vigente). Un certificado ANULADO sigue listado como no válido y sin descarga. Vencer o
 * cancelar la matrícula no elimina nada: el listado sale de los certificados emitidos, no de la matrícula.
 */
@Service
@RequiredArgsConstructor
public class MisCertificadosService {

    private final CertificadoRepository certificados;
    private final CurrentUserService actual;

    @Transactional(readOnly = true)
    public List<MiCertificadoResponse> listar() {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
        return certificados.findByLogro_Matricula_Usuario_IdOrderByEmitidoEnDesc(actual.get().userId()).stream()
                .map(c -> {
                    boolean vigente = Certificado.VIGENTE.equals(c.getEstado());
                    return new MiCertificadoResponse(c.getCodigo(), c.getLogro().getMatricula().getCurso().getId(),
                            c.getTituloCursoImpreso(), c.getNivel(), c.getEntidadImpresa(), c.getHorasImpresas(),
                            c.getEmitidoEn(), vigente ? Certificado.VIGENTE : Certificado.ANULADO, c.getAnuladoEn(),
                            vigente ? "/api/app/certificados/" + c.getCodigo() + "/descargar" : null);
                }).toList();
    }
}
