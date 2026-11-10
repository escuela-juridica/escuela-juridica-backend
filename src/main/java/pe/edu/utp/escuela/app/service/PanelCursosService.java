package pe.edu.utp.escuela.app.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.MatriculaResponse;
import pe.edu.utp.escuela.app.dto.MiCursoResponse;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.PanelCursosResponse;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;

@Service
@RequiredArgsConstructor
public class PanelCursosService {

    public static final String EMITIDO = "EMITIDO";
    public static final String ANULADO = "ANULADO";
    public static final String SIN_EMITIR = "SIN_EMITIR";

    private final MatriculaService matriculas;
    private final CertificadoRepository certificados;

    @Transactional
    public PanelCursosResponse listar(String estado, String busqueda, Pageable pageable) {
        boolean completados = "COMPLETADOS".equalsIgnoreCase(estado);
        Page<MatriculaResponse> pagina = matriculas.misCursosPaginados(completados, busqueda, pageable);
        List<Long> ids = pagina.getContent().stream().map(MatriculaResponse::id).toList();
        Map<Long, String> estados = ids.isEmpty() ? Map.of()
                : certificados.findByLogro_Matricula_IdIn(ids).stream().collect(Collectors.toMap(
                        c -> c.getLogro().getMatricula().getId(),
                        c -> Certificado.VIGENTE.equals(c.getEstado()) ? EMITIDO : ANULADO,
                        (a, b) -> EMITIDO.equals(a) ? a : b));
        Page<MiCursoResponse> cursos = pagina.map(m -> new MiCursoResponse(m, estados.getOrDefault(m.id(), SIN_EMITIR)));
        return new PanelCursosResponse(PageResponse.from(cursos), matriculas.contarMisCursos(false),
                matriculas.contarMisCursos(true));
    }
}
