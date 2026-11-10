package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.MaterialAlumnoResponse;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.ProgresoMaterial;
import pe.edu.utp.escuela.app.entity.Recurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.LeccionRepository;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.ProgresoMaterialRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

@Service
@RequiredArgsConstructor
public class MaterialAlumnoService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final LeccionRepository lecciones;
    private final MaterialLeccionRepository materiales;
    private final MatriculaRepository matriculas;
    private final ProgresoMaterialRepository progresoMateriales;
    private final ArchivoAlmacenamientoService almacenamiento;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MaterialAlumnoResponse> listar(Long leccionId) {
        requiereAlumno();
        Leccion leccion = lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("La lección no existe."));
        Matricula matricula = verificarAccesoAlCurso(leccion);

        List<MaterialLeccion> activos = materiales.findByLeccion_IdOrderByOrdenAsc(leccionId).stream()
                .filter(MaterialLeccion::isActivo)
                .toList();
        Map<Long, ProgresoMaterial> progreso = activos.isEmpty() ? Map.of()
                : progresoMateriales.findByMatricula_IdAndMaterialLeccion_IdIn(
                        matricula.getId(), activos.stream().map(MaterialLeccion::getId).toList()).stream()
                        .collect(Collectors.toMap(p -> p.getMaterialLeccion().getId(), p -> p, (a, b) -> a));
        return activos.stream().map(m -> MaterialAlumnoResponse.de(m, progreso.get(m.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ContenidoMaterial contenido(Long materialId) {
        MaterialLeccion material = cargarMaterialAutorizado(materialId);
        return transmitir(material);
    }

    @Transactional(readOnly = true)
    public ContenidoMaterial descargar(Long materialId) {
        MaterialLeccion material = cargarMaterialAutorizado(materialId);
        if (!material.isPermiteDescarga()) {
            throw new BusinessValidationException("Este material no permite descarga.");
        }
        return transmitir(material);
    }

    private MaterialLeccion cargarMaterialAutorizado(Long materialId) {
        requiereAlumno();
        MaterialLeccion material = materiales.findById(materialId)
                .filter(MaterialLeccion::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("El material no existe."));
        verificarAccesoAlCurso(material.getLeccion());
        return material;
    }

    private ContenidoMaterial transmitir(MaterialLeccion material) {
        Recurso recurso = material.getRecurso();
        if (!"SUBIDO".equals(recurso.getOrigen())) {
            throw new BusinessValidationException("Este material no tiene contenido para transmitir.");
        }
        Resource archivo = almacenamiento.cargar(recurso.getReferencia());
        return new ContenidoMaterial(archivo, recurso.getNombreArchivo(), recurso.getTipoMime());
    }

    private void requiereAlumno() {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
    }

    private Matricula verificarAccesoAlCurso(Leccion leccion) {
        Curso curso = leccion.getModulo().getCurso();
        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), curso.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));
        verificarAcceso(matricula);
        return matricula;
    }

    private void verificarAcceso(Matricula m) {
        Instant ahora = clock.instant();
        boolean vigente = m.getFechaVencimiento() == null || !ahora.isAfter(m.getFechaVencimiento());
        boolean inicio = m.getCurso().getFechaInicio() == null
                || !LocalDate.now(clock.withZone(LIMA)).isBefore(m.getCurso().getFechaInicio());
        boolean cuentaHabilitada = m.getUsuario().isActivo() && m.getUsuario().getCorreoVerificadoEn() != null
                && !m.getUsuario().isRequiereCambioContrasena();
        boolean cancelada = "CANCELADA".equals(m.getEstado());
        boolean efectivo = !cancelada && "ACTIVA".equals(m.getEstado()) && vigente && inicio && cuentaHabilitada;
        if (efectivo) {
            return;
        }

        if (AccesoPorCancelacion.permiteLectura(m, cuentaHabilitada, LIMA)) {
            return;
        }

        throw new BusinessValidationException("No tienes acceso a este contenido.");
    }

    public record ContenidoMaterial(Resource recurso, String nombreArchivo, String tipoMime) {
    }
}
