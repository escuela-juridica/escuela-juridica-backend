package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.MaterialAlumnoRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Recurso;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/** HU-023 — Consultar materiales protegidos: cada solicitud (listar, ver contenido o descargar)
 * revalida identidad, matrícula, acceso efectivo y pertenencia al curso antes de entregar nada,
 * así que compartir la URL de un endpoint de este servicio no le da acceso a quien no está
 * matriculado. Deliberadamente no revisa bloqueo por secuencia obligatoria: eso es dominio de
 * HU-022/navegación del aula, ya resuelto ahí; esta historia solo pide identidad, matrícula,
 * estado de acceso y pertenencia al curso.
 *
 * <p>Los materiales de origen SUBIDO nunca se exponen con su ruta real de
 * {@code /uploads/...}: este servicio transmite los bytes él mismo a través de
 * {@link ArchivoAlmacenamientoServicio#cargar(String)}, así lo único que ve el alumno es un
 * endpoint propio que vuelve a autorizar en cada llamada. */
@Service
@RequiredArgsConstructor
public class MaterialAlumnoServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final LeccionRepositorio lecciones;
    private final MaterialLeccionRepositorio materiales;
    private final MatriculaRepositorio matriculas;
    private final ArchivoAlmacenamientoServicio almacenamiento;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MaterialAlumnoRespuesta> listar(Long leccionId) {
        requiereAlumno();
        Leccion leccion = lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("La lección no existe."));
        verificarAccesoAlCurso(leccion);

        return materiales.findByLeccion_IdOrderByOrdenAsc(leccionId).stream()
                .filter(MaterialLeccion::isActivo)
                .map(MaterialAlumnoRespuesta::de)
                .toList();
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

    private void verificarAccesoAlCurso(Leccion leccion) {
        Curso curso = leccion.getModulo().getCurso();
        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), curso.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));
        verificarAcceso(matricula);
    }

    /** Replica exactamente {@code AulaServicio#verificarAcceso} (y, en última instancia,
     * {@code MatriculaServicio#respuesta}): es la definición oficial de acceso efectivo en todo
     * el proyecto y no debe divergir. Se duplica a propósito en vez de extraerla a un helper
     * compartido, siguiendo la convención ya usada entre esos dos servicios. */
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

        boolean cursoCancelado = "CANCELADO".equals(m.getCurso().getEstadoCurso().getCodigo());
        boolean accesoLecturaPorCancelacion = cursoCancelado && !cancelada && cuentaHabilitada;
        if (accesoLecturaPorCancelacion) {
            return;
        }

        throw new BusinessValidationException("No tienes acceso a este contenido.");
    }

    /** Resultado de resolver un material SUBIDO para transmitir sus bytes por streaming. */
    public record ContenidoMaterial(Resource recurso, String nombreArchivo, String tipoMime) {
    }
}
