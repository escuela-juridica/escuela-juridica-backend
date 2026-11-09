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
                    return new MiCertificadoResponse(c.getCodigo(), c.getNombresImpresos(), c.getLogro().getMatricula().getCurso().getId(),
                            c.getTituloCursoImpreso(), c.getNivel(), c.getEntidadImpresa(), c.getHorasImpresas(),
                            c.getEmitidoEn(), vigente ? Certificado.VIGENTE : Certificado.ANULADO, c.getAnuladoEn(),
                            vigente ? "/api/app/certificados/" + c.getCodigo() + "/descargar" : null);
                }).toList();
    }
}
