package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CancelarMatriculaPeticion;
import pe.edu.utp.escuela.app.dto.CrearMatriculaAdministrativaPeticion;
import pe.edu.utp.escuela.app.dto.MatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.PagoRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/** Nucleo transaccional compartido por las historias de matricula de EP03. */
@Service @RequiredArgsConstructor
public class MatriculaServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final MatriculaRepositorio matriculas;
    private final CursoRepositorio cursos;
    private final UsuarioRepositorio usuarios;
    private final UsuarioRolRepositorio roles;
    private final AdminUsuariosServicio adminUsuarios;
    private final PagoRepositorio pagos;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional
    public MatriculaRespuesta matricularGratis(Long cursoId) {
        Usuario usuario = usuarioActualAlumno();
        Curso curso = curso(cursoId);
        if (!"GRATUITO".equals(curso.getTipoVenta())) throw new BusinessValidationException("Este curso no es gratuito.");
        return crearActiva(usuario, curso, "GRATUITA", null);
    }

    @Transactional
    public MatriculaRespuesta matricularAdministrativamente(CrearMatriculaAdministrativaPeticion p) {
        exigirAdministrador();
        Usuario alumno = usuarios.findWithPersonaById(p.usuarioId()).orElseThrow(() -> new ResourceNotFoundException("El alumno no existe."));
        if (!tieneRol(alumno.getId(), "ALUMNO")) throw new BusinessValidationException("Solo puedes matricular cuentas con rol Alumno.");
        Curso curso = curso(p.cursoId());
        if (!"REGISTRADO_MANUAL".equals(p.condicionEconomica()) && !"EXONERADO".equals(p.condicionEconomica())) throw new BusinessValidationException("La condicion economica no es valida.");
        MatriculaRespuesta respuesta = crearActiva(alumno, curso, "ADMINISTRADOR", p.motivo());
        Matricula m = matriculas.getReferenceById(respuesta.id());
        BigDecimal importe = p.importe() == null ? BigDecimal.ZERO : p.importe();
        if ("EXONERADO".equals(p.condicionEconomica()) && importe.signum() != 0) throw new BusinessValidationException("Una exoneracion debe tener importe cero.");
        if ("REGISTRADO_MANUAL".equals(p.condicionEconomica()) && (importe.signum() <= 0 || p.medio() == null || p.referencia() == null)) throw new BusinessValidationException("El pago manual exige importe, medio y referencia.");
        Pago pago = new Pago(); pago.setMatricula(m); pago.setNumeroPedido("ADM-" + m.getId());
        pago.setOrigen("EXONERADO".equals(p.condicionEconomica()) ? "EXONERADO" : "MANUAL");
        pago.setEstado(p.condicionEconomica()); pago.setImporte(importe); pago.setMedio(p.medio());
        pago.setPrecioRegularAplicado(curso.getPrecioRegular());
        pago.setPrecioPromocionalAplicado(curso.getPrecioPromocional());
        pago.setReferenciaExterna(p.referencia()); pago.setMotivo(p.motivo()); pago.setResultadoEn(clock.instant());
        pago.setRegistradoPorUsuarioId(actual.get().userId()); pagos.save(pago);
        return respuesta;
    }

    @Transactional(readOnly = true)
    public List<MatriculaRespuesta> misCursos() {
        Usuario usuario = usuarioActualAlumno();
        return matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(usuario.getId()).stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public List<MatriculaAdministrativaRespuesta> listarAdministrativas(String texto, String estado) {
        exigirAdministrador();
        String filtro = texto == null ? "" : texto.strip().toLowerCase();
        String estadoFiltro = estado == null ? "" : estado.strip().toUpperCase();
        return matriculas.findAllByOrderByFechaMatriculaDesc().stream()
                .filter(m -> estadoFiltro.isBlank() || estadoFiltro.equals(m.getEstado()))
                .filter(m -> filtro.isBlank() || m.getCurso().getTitulo().toLowerCase().contains(filtro)
                        || m.getUsuario().getCorreo().toLowerCase().contains(filtro)
                        || (m.getUsuario().getPersona().getNombres() + " " + m.getUsuario().getPersona().getApellidoPaterno()).toLowerCase().contains(filtro))
                .map(this::respuestaAdministrativa).toList();
    }

    @Transactional(readOnly = true)
    public List<ReporteMatriculaRespuesta> reporte(String texto, String estado) {
        exigirAdministrador();
        String filtro = texto == null ? "" : texto.strip().toLowerCase();
        String estadoFiltro = estado == null ? "" : estado.strip().toUpperCase();
        return matriculas.findAllByOrderByFechaMatriculaDesc().stream()
                .filter(m -> estadoFiltro.isBlank() || estadoFiltro.equals(m.getEstado()))
                .filter(m -> filtro.isBlank() || m.getCurso().getTitulo().toLowerCase().contains(filtro)
                        || m.getUsuario().getCorreo().toLowerCase().contains(filtro))
                .map(m -> new ReporteMatriculaRespuesta(m.getId(),
                        (m.getUsuario().getPersona().getNombres() + " " + m.getUsuario().getPersona().getApellidoPaterno()).strip(),
                        m.getUsuario().getCorreo(), m.getCurso().getTitulo(), m.getCurso().getModalidad(),
                        m.getFechaMatricula(), m.getFechaActivacion(), m.getEstado(), m.getFormaIngreso(),
                        m.getFechaFinalizacion() == null ? "EN_CURSO" : "FINALIZADO"))
                .toList();
    }

    @Transactional
    public MatriculaRespuesta cancelar(Long matriculaId, CancelarMatriculaPeticion p) {
        exigirAdministrador(); Matricula m = matriculas.findById(matriculaId).orElseThrow(() -> new ResourceNotFoundException("La matricula no existe."));
        if (!"ACTIVA".equals(m.getEstado())) throw new BusinessValidationException("Solo puedes cancelar una matricula activa.");
        m.setEstado("CANCELADA"); m.setMotivoCancelacion(p.motivo().strip()); m.setCanceladaEn(clock.instant()); m.setCanceladaPorUsuarioId(actual.get().userId());
        return respuesta(m);
    }

    private MatriculaRespuesta crearActiva(Usuario usuario, Curso curso, String forma, String motivo) {
        if (matriculas.existsByUsuario_IdAndCurso_Id(usuario.getId(), curso.getId())) throw new DuplicateResourceException("El alumno ya tiene una matricula en este curso.");
        validarCurso(curso, "GRATUITA".equals(forma)); validarCupo(curso);
        Instant ahora = clock.instant(); Matricula m = new Matricula(); m.setUsuario(usuario); m.setCurso(curso); m.setEstado("ACTIVA"); m.setFormaIngreso(forma); m.setFechaMatricula(ahora); m.setFechaActivacion(ahora); m.setCreadoPorUsuarioId("GRATUITA".equals(forma) ? null : actual.get().userId());
        m.setFechaVencimiento(vencimiento(curso, ahora)); matriculas.saveAndFlush(m); return respuesta(m);
    }

    private void validarCurso(Curso c, boolean gratuita) {
        String e = c.getEstadoCurso().getCodigo(); if (!("PUBLICADO".equals(e) || (!gratuita && "EN_CURSO".equals(e)))) throw new BusinessValidationException("El curso no admite nuevas matriculas.");
        LocalDate hoy = LocalDate.now(clock.withZone(LIMA)); if (gratuita && c.getFechaCierreMatricula() != null && hoy.isAfter(c.getFechaCierreMatricula())) throw new BusinessValidationException("El cierre de matricula ya finalizo.");
    }
    private void validarCupo(Curso c) { if (c.getCupoMaximo() != null && matriculas.contarActivas(List.of(c.getId())).getOrDefault(c.getId(), 0L) >= c.getCupoMaximo()) throw new BusinessValidationException("El curso ya no tiene cupos disponibles."); }
    private Instant vencimiento(Curso c, Instant activacion) { if (c.getVigenciaAccesoDias() == null) return null; LocalDate base = LocalDate.ofInstant(activacion, LIMA); if (c.getFechaInicio() != null && c.getFechaInicio().isAfter(base)) base = c.getFechaInicio(); return base.plusDays(c.getVigenciaAccesoDias() - 1L).atTime(23,59,59).atZone(LIMA).toInstant(); }
    private Usuario usuarioActualAlumno() { Usuario u = usuarios.findWithPersonaById(actual.get().userId()).orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe.")); if (!u.isActivo() || u.getCorreoVerificadoEn() == null || u.isRequiereCambioContrasena()) throw new BusinessValidationException("Completa la habilitacion de tu cuenta para usar matriculas."); if (!actual.get().hasRole("ALUMNO")) throw new BusinessValidationException("Tu cuenta no tiene rol Alumno."); return u; }
    private boolean tieneRol(Long usuarioId, String codigo) {
        return adminUsuarios.obtener(usuarioId).roles().stream().anyMatch(rol -> codigo.equals(rol.name()));
    }
    private Curso curso(Long id) { return cursos.findWithDetalleById(id).orElseThrow(() -> new ResourceNotFoundException("El curso no existe.")); }
    private void exigirAdministrador() { if (!actual.get().hasRole("ADMINISTRADOR")) throw new ForbiddenException(); }
    private MatriculaAdministrativaRespuesta respuestaAdministrativa(Matricula m) {
        String alumno = m.getUsuario().getPersona().getNombres() + " " + m.getUsuario().getPersona().getApellidoPaterno();
        return new MatriculaAdministrativaRespuesta(m.getId(), m.getUsuario().getId(), alumno.strip(), m.getUsuario().getCorreo(), m.getCurso().getId(), m.getCurso().getTitulo(), m.getEstado(), m.getFormaIngreso(), m.getFechaMatricula(), m.getFechaVencimiento());
    }
    private MatriculaRespuesta respuesta(Matricula m) { Instant ahora = clock.instant(); boolean vigente = m.getFechaVencimiento() == null || !ahora.isAfter(m.getFechaVencimiento()); boolean inicio = m.getCurso().getFechaInicio() == null || !LocalDate.now(clock.withZone(LIMA)).isBefore(m.getCurso().getFechaInicio()); boolean efectivo = "ACTIVA".equals(m.getEstado()) && vigente && inicio && m.getUsuario().isActivo() && m.getUsuario().getCorreoVerificadoEn() != null && !m.getUsuario().isRequiereCambioContrasena(); String mensaje = efectivo ? "Disponible para continuar." : !inicio ? "El curso aun no inicia." : !vigente ? "El acceso vencio." : "El acceso no esta disponible."; return new MatriculaRespuesta(m.getId(), m.getCurso().getId(), m.getCurso().getTitulo(), m.getCurso().getUrlAmigable(), m.getCurso().getModalidad(), m.getEstado(), m.getFormaIngreso(), m.getFechaMatricula(), m.getFechaActivacion(), m.getFechaVencimiento(), m.getFechaFinalizacion(), m.getCurso().getFechaInicio(), efectivo, mensaje); }
}
