package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AdvertenciaMatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.CancelarMatriculaPeticion;
import pe.edu.utp.escuela.app.dto.CrearMatriculaAdministrativaPeticion;
import pe.edu.utp.escuela.app.dto.HistorialEstadoMatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaDetalleAdministrativaRespuesta;
import pe.edu.utp.escuela.app.dto.MatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.PagoMatriculaDetalleRespuesta;
import pe.edu.utp.escuela.app.dto.ReenvioMatriculaRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.HistorialEstadoMatricula;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.CursoRepositorio;
import pe.edu.utp.escuela.app.repository.HistorialEstadoMatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.NotificacionRepositorio;
import pe.edu.utp.escuela.app.repository.PagoRepositorio;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

@Service
@RequiredArgsConstructor
public class MatriculaServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final MatriculaRepositorio matriculas;
    private final CursoRepositorio cursos;
    private final UsuarioRepositorio usuarios;
    private final UsuarioRolRepositorio roles;
    private final AdminUsuariosServicio adminUsuarios;
    private final PagoRepositorio pagos;
    private final HistorialEstadoMatriculaRepositorio historial;
    private final NotificacionRepositorio notificaciones;
    private final ReglaCursoRepositorio reglas;
    private final ModuloRepositorio modulos;
    private final LeccionRepositorio lecciones;
    private final ProgresoLeccionRepositorio progresoLecciones;
    private final MailService mailService;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional
    public MatriculaRespuesta matricularGratis(Long cursoId) {
        procesarVencimientos();
        Usuario usuario = usuarioActualAlumno();
        Curso curso = curso(cursoId);
        if (!"GRATUITO".equals(curso.getTipoVenta())) {
            throw new BusinessValidationException("Este curso no es gratuito.");
        }
        validarSesionesFuturas(curso, true);
        Matricula matricula = crearActiva(usuario, curso, "GRATUITA", null);
        return respuesta(matricula, enviarConfirmacion(matricula, "El curso es gratuito; no se realizó ningún cobro."));
    }

    @Transactional
    public MatriculaRespuesta matricularAdministrativamente(CrearMatriculaAdministrativaPeticion p) {
        procesarVencimientos();
        exigirAdministrador();
        Usuario alumno = usuarios.findWithPersonaById(p.usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("El alumno no existe."));
        if (!tieneRol(alumno.getId(), "ALUMNO")) {
            throw new BusinessValidationException("Solo puedes matricular cuentas con rol Alumno.");
        }
        Curso curso = curso(p.cursoId());
        if (!"REGISTRADO_MANUAL".equals(p.condicionEconomica()) && !"EXONERADO".equals(p.condicionEconomica())) {
            throw new BusinessValidationException("La condición económica no es válida.");
        }
        AdvertenciaMatriculaRespuesta advertencia = advertenciaAdministrativa(curso.getId());
        if (advertencia.requiereConfirmacion() && !p.confirmoAdvertenciaAcademica()) {
            throw new BusinessValidationException(advertencia.mensaje());
        }

        Matricula matricula = crearActiva(alumno, curso, "ADMINISTRADOR", p.motivo());
        BigDecimal importe = p.importe() == null ? BigDecimal.ZERO : p.importe();
        if ("EXONERADO".equals(p.condicionEconomica()) && importe.signum() != 0) {
            throw new BusinessValidationException("Una exoneración debe tener importe cero.");
        }
        if ("REGISTRADO_MANUAL".equals(p.condicionEconomica())
                && (importe.signum() <= 0 || esVacio(p.medio()) || esVacio(p.referencia()))) {
            throw new BusinessValidationException("El pago manual exige importe, medio y referencia.");
        }
        Pago pago = new Pago();
        pago.setMatricula(matricula);
        pago.setNumeroPedido("ADM-" + matricula.getId());
        pago.setOrigen("EXONERADO".equals(p.condicionEconomica()) ? "EXONERADO" : "MANUAL");
        pago.setEstado(p.condicionEconomica());
        pago.setImporte(importe);
        pago.setMedio(limpiar(p.medio()));
        pago.setPrecioRegularAplicado(curso.getPrecioRegular());
        pago.setPrecioPromocionalAplicado(curso.getPrecioPromocional());
        pago.setReferenciaExterna(limpiar(p.referencia()));
        pago.setMotivo(p.motivo().strip());
        pago.setResultadoEn(clock.instant());
        pago.setRegistradoPorUsuarioId(actual.get().userId());
        pagos.save(pago);
        String condicion = "EXONERADO".equals(p.condicionEconomica())
                ? "Matrícula administrativa exonerada; no se registró un cobro."
                : "Pago registrado manualmente por administración.";
        return respuesta(matricula, enviarConfirmacion(matricula, condicion));
    }

    @Transactional(readOnly = true)
    public AdvertenciaMatriculaRespuesta consultarAdvertenciaAdministrativa(Long cursoId) {
        exigirAdministrador();
        return advertenciaAdministrativa(curso(cursoId).getId());
    }

    @Transactional
    public List<MatriculaRespuesta> misCursos() {
        procesarVencimientos();
        Usuario usuario = usuarioActualAlumno();
        return matriculas.findByUsuario_IdOrderByFechaMatriculaDesc(usuario.getId()).stream()
                .map(m -> respuesta(m, null)).toList();
    }

    @Transactional
    public Page<MatriculaAdministrativaRespuesta> listarAdministrativas(String texto, String estado, Pageable pageable) {
        procesarVencimientos();
        exigirAdministrador();
        return matriculas.buscarAdministrativas(normalizar(texto), normalizarEstado(estado), pageable);
    }

    @Transactional
    public Page<ReporteMatriculaRespuesta> reportePaginado(
            String texto, String estado, Long cursoId, String modalidad,
            LocalDate desde, LocalDate hasta, Pageable pageable) {
        procesarVencimientos();
        exigirAdministrador();
        validarRango(desde, hasta);
        Page<ReporteMatriculaRespuesta> pagina = matriculas.buscarReporte(normalizar(texto), normalizarEstado(estado), cursoId,
                normalizarEstado(modalidad), inicio(desde), finExclusivo(hasta), pageable);
        Map<Long, String> origenes = origenesReporte(pagina.getContent());
        return pagina.map(f -> conOrigen(f, origenes.getOrDefault(f.matriculaId(), f.formaIngreso())));
    }

    @Transactional
    public List<ReporteMatriculaRespuesta> reporte(
            String texto, String estado, Long cursoId, String modalidad, LocalDate desde, LocalDate hasta) {
        procesarVencimientos();
        exigirAdministrador();
        validarRango(desde, hasta);
        List<ReporteMatriculaRespuesta> filas = matriculas.exportarReporte(normalizar(texto), normalizarEstado(estado), cursoId,
                normalizarEstado(modalidad), inicio(desde), finExclusivo(hasta));
        Map<Long, String> origenes = origenesReporte(filas);
        return filas.stream().map(f -> conOrigen(f, origenes.getOrDefault(f.matriculaId(), f.formaIngreso()))).toList();
    }

    @Transactional
    public MatriculaDetalleAdministrativaRespuesta detalleAdministrativo(Long matriculaId) {
        procesarVencimientos();
        exigirAdministrador();
        Matricula m = matriculas.findWithDetalleById(matriculaId)
                .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
        List<PagoMatriculaDetalleRespuesta> pagosRespuesta = pagos.findByMatricula_IdOrderByResultadoEnDesc(matriculaId)
                .stream().map(p -> new PagoMatriculaDetalleRespuesta(p.getId(), p.getOrigen(), p.getEstado(),
                        p.getImporte(), p.getMoneda(), p.getMedio(), p.getReferenciaExterna(), p.getMotivo(),
                        p.getRegistradoPorUsuarioId(), nombreUsuario(p.getRegistradoPorUsuarioId()), p.getResultadoEn())).toList();
        List<HistorialEstadoMatriculaRespuesta> estados = historial.findByMatricula_IdOrderByRealizadoEnDesc(matriculaId)
                .stream().map(h -> new HistorialEstadoMatriculaRespuesta(h.getEstadoAnterior(), h.getEstadoNuevo(),
                        h.getMotivo(), h.getRealizadoPorUsuarioId(), nombreUsuario(h.getRealizadoPorUsuarioId()), h.getRealizadoEn())).toList();
        return new MatriculaDetalleAdministrativaRespuesta(m.getId(), m.getUsuario().getId(),
                m.getUsuario().getPersona().nombreCompleto(), m.getUsuario().getCorreo(), m.getCurso().getId(),
                m.getCurso().getTitulo(), m.getCurso().getModalidad(), m.getEstado(), m.getFormaIngreso(),
                m.getFechaMatricula(), m.getFechaActivacion(), m.getFechaVencimiento(), m.getFechaFinalizacion(),
                m.getMotivoCancelacion(), m.getCreadoPorUsuarioId(), nombreUsuario(m.getCreadoPorUsuarioId()), pagosRespuesta, estados);
    }

    @Transactional
    public ReenvioMatriculaRespuesta reenviarConfirmacion(Long matriculaId) {
        procesarVencimientos();
        Matricula m = matriculas.findWithDetalleById(matriculaId)
                .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
        if (!actual.get().hasRole("ADMINISTRADOR") && !m.getUsuario().getId().equals(actual.get().userId())) {
            throw new ForbiddenException();
        }
        String estado = enviarConfirmacion(m, m.getFormaIngreso().equals("GRATUITA")
                ? "El curso es gratuito; no se realizó ningún cobro."
                : "Confirmación de matrícula administrativa.");
        return new ReenvioMatriculaRespuesta("ENVIADO".equals(estado),
                "ENVIADO".equals(estado) ? "Correo enviado correctamente." : "No se pudo enviar el correo. Puedes volver a intentarlo.");
    }

    @Transactional
    public MatriculaRespuesta cancelar(Long matriculaId, CancelarMatriculaPeticion p) {
        procesarVencimientos();
        exigirAdministrador();
        Matricula m = matriculas.findWithDetalleById(matriculaId)
                .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
        if (!"ACTIVA".equals(m.getEstado())) {
            throw new BusinessValidationException("Solo puedes cancelar una matrícula activa.");
        }
        String anterior = m.getEstado();
        m.setEstado("CANCELADA");
        m.setMotivoCancelacion(p.motivo().strip());
        m.setCanceladaEn(clock.instant());
        m.setCanceladaPorUsuarioId(actual.get().userId());
        registrarCambio(m, anterior, "CANCELADA", p.motivo().strip(), actual.get().userId());
        return respuesta(m, null);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    @Transactional
    public void procesarVencimientosProgramados() {
        procesarVencimientos();
    }

    private void procesarVencimientos() {
        Instant ahora = clock.instant();
        List<Matricula> vencidas = matriculas.findByEstadoAndFechaVencimientoLessThanEqual("ACTIVA", ahora);
        for (Matricula matricula : vencidas) {
            String anterior = matricula.getEstado();
            matricula.setEstado("VENCIDA");
            registrarCambio(matricula, anterior, "VENCIDA", "Vigencia de acceso agotada.", null);
        }
    }

    private Matricula crearActiva(Usuario usuario, Curso curso, String forma, String motivo) {
        Curso cursoBloqueado = cursos.bloquearParaMatricula(curso.getId())
                .orElseThrow(() -> new ResourceNotFoundException("El curso no existe."));
        if (matriculas.existsByUsuario_IdAndCurso_Id(usuario.getId(), cursoBloqueado.getId())) {
            throw new DuplicateResourceException("El alumno ya tiene una matrícula en este curso.");
        }
        validarCurso(cursoBloqueado, "GRATUITA".equals(forma));
        validarCupo(cursoBloqueado);
        Instant ahora = clock.instant();
        Matricula m = new Matricula();
        m.setUsuario(usuario);
        m.setCurso(cursoBloqueado);
        m.setEstado("ACTIVA");
        m.setFormaIngreso(forma);
        m.setFechaMatricula(ahora);
        m.setFechaActivacion(ahora);
        m.setCreadoPorUsuarioId("GRATUITA".equals(forma) ? null : actual.get().userId());
        m.setFechaVencimiento(vencimiento(cursoBloqueado, ahora));
        matriculas.saveAndFlush(m);
        registrarCambio(m, null, "ACTIVA", motivo, m.getCreadoPorUsuarioId());
        return m;
    }

    private AdvertenciaMatriculaRespuesta advertenciaAdministrativa(Long cursoId) {
        Curso curso = curso(cursoId);
        ReglaCurso regla = reglas.findByCurso_Id(cursoId).orElse(null);
        boolean requiere = regla != null && regla.isRequiereAsistencia()
                && ("EN_VIVO".equals(curso.getModalidad()) || "HIBRIDO".equals(curso.getModalidad()));
        boolean sinSesionesFuturas = requiere
                && lecciones.contarSesionesFuturasActivas(cursoId, clock.instant()) == 0;
        return sinSesionesFuturas
                ? new AdvertenciaMatriculaRespuesta(true,
                        "El curso exige asistencia y no tiene sesiones futuras. El alumno podría no cumplir la condición de certificación automática.")
                : new AdvertenciaMatriculaRespuesta(false, "");
    }

    private void validarSesionesFuturas(Curso curso, boolean autoservicio) {
        AdvertenciaMatriculaRespuesta advertencia = advertenciaAdministrativa(curso.getId());
        if (autoservicio && advertencia.requiereConfirmacion()) {
            throw new BusinessValidationException("No es posible matricularse automáticamente: " + advertencia.mensaje());
        }
    }

    private void validarCurso(Curso curso, boolean gratuita) {
        String estado = curso.getEstadoCurso().getCodigo();
        if (!("PUBLICADO".equals(estado) || "EN_CURSO".equals(estado))) {
            throw new BusinessValidationException("El curso no admite nuevas matrículas.");
        }
        LocalDate hoy = LocalDate.now(clock.withZone(LIMA));
        if (gratuita && curso.getFechaCierreMatricula() != null && hoy.isAfter(curso.getFechaCierreMatricula())) {
            throw new BusinessValidationException("El cierre de matrícula ya finalizó.");
        }
    }

    private void validarCupo(Curso curso) {
        if (curso.getCupoMaximo() != null
                && matriculas.contarActivas(List.of(curso.getId())).getOrDefault(curso.getId(), 0L) >= curso.getCupoMaximo()) {
            throw new BusinessValidationException("El curso ya no tiene cupos disponibles.");
        }
    }

    private Instant vencimiento(Curso curso, Instant activacion) {
        if (curso.getVigenciaAccesoDias() == null) return null;
        LocalDate base = LocalDate.ofInstant(activacion, LIMA);
        if (curso.getFechaInicio() != null && curso.getFechaInicio().isAfter(base)) base = curso.getFechaInicio();
        return base.plusDays(curso.getVigenciaAccesoDias() - 1L).atTime(23, 59, 59).atZone(LIMA).toInstant();
    }

    private Usuario usuarioActualAlumno() {
        Usuario u = usuarios.findWithPersonaById(actual.get().userId())
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe."));
        if (!u.isActivo() || u.getCorreoVerificadoEn() == null || u.isRequiereCambioContrasena()) {
            throw new BusinessValidationException("Completa la habilitación de tu cuenta para usar matrículas.");
        }
        if (!actual.get().hasRole("ALUMNO")) {
            throw new BusinessValidationException("Tu cuenta no tiene rol Alumno.");
        }
        return u;
    }

    private MatriculaRespuesta respuesta(Matricula m, String estadoCorreo) {
        Instant ahora = clock.instant();
        boolean vigente = m.getFechaVencimiento() == null || !ahora.isAfter(m.getFechaVencimiento());
        boolean inicio = m.getCurso().getFechaInicio() == null
                || !LocalDate.now(clock.withZone(LIMA)).isBefore(m.getCurso().getFechaInicio());
        boolean efectivo = "ACTIVA".equals(m.getEstado()) && vigente && inicio && m.getUsuario().isActivo()
                && m.getUsuario().getCorreoVerificadoEn() != null && !m.getUsuario().isRequiereCambioContrasena();
        String mensaje = efectivo ? "Disponible para continuar."
                : !inicio ? "El curso aún no inicia."
                : !vigente || "VENCIDA".equals(m.getEstado()) ? "El acceso venció."
                : "El acceso no está disponible.";
        List<Modulo> modulosCurso = modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(m.getCurso().getId());
        List<Long> idsModulos = modulosCurso.stream().map(Modulo::getId).toList();
        List<Leccion> obligatorias = idsModulos.isEmpty() ? List.of()
                : lecciones.buscarActivasDeModulos(idsModulos).stream().filter(Leccion::isEsObligatoria).toList();
        Map<Long, Boolean> completadas = progresoLecciones.findByMatricula_Id(m.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLeccion().getId(), ProgresoLeccion::isCompletada, (a, b) -> a));
        long total = obligatorias.size();
        long hechas = obligatorias.stream().filter(l -> Boolean.TRUE.equals(completadas.get(l.getId()))).count();
        int porcentaje = total == 0 ? 0 : (int) Math.round(hechas * 100.0 / total);
        String siguiente = obligatorias.stream().filter(l -> !Boolean.TRUE.equals(completadas.get(l.getId())))
                .map(Leccion::getTitulo).findFirst().orElse(total == 0 ? "Aún no hay lecciones obligatorias" : "Lecciones obligatorias completadas");
        String estadoCorreoActual = estadoCorreo != null ? estadoCorreo
                : notificaciones.findTopByTipoOrderByCreadoEnDesc("MAT_" + m.getId() + "_CONF")
                        .map(Notificacion::getEstadoEnvio).orElse(null);
        return new MatriculaRespuesta(m.getId(), m.getCurso().getId(), m.getCurso().getTitulo(),
                m.getCurso().getUrlAmigable(), m.getCurso().getImagenPortadaUrl(), m.getCurso().getModalidad(), m.getEstado(), m.getFormaIngreso(),
                m.getFechaMatricula(), m.getFechaActivacion(), m.getFechaVencimiento(), m.getFechaFinalizacion(),
                m.getCurso().getFechaInicio(), efectivo, mensaje, porcentaje, (int) hechas, (int) total, siguiente,
                estadoCorreoActual);
    }

    private String enviarConfirmacion(Matricula matricula, String origen) {
        String tipo = "MAT_" + matricula.getId() + "_CONF";
        Notificacion n = notificaciones.findTopByTipoOrderByCreadoEnDesc(tipo).orElseGet(() -> {
            Notificacion nueva = new Notificacion();
            nueva.setUsuario(matricula.getUsuario());
            nueva.setTipo(tipo);
            nueva.setDestinatario(matricula.getUsuario().getCorreo());
            nueva.setAsunto("Matrícula confirmada: " + matricula.getCurso().getTitulo());
            return nueva;
        });
        n.setIntentosEnvio(n.getIntentosEnvio() + 1);
        try {
            String fechaAcceso = matricula.getCurso().getFechaInicio() != null
                    && matricula.getCurso().getFechaInicio().isAfter(LocalDate.now(clock.withZone(LIMA)))
                    ? "Desde el " + matricula.getCurso().getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "Inmediato, al iniciar sesión con tu cuenta habilitada";
            mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(),
                    "mail/confirmacion-matricula.html", Map.of(
                            "nombre", matricula.getUsuario().getPersona().getNombres(),
                            "curso", matricula.getCurso().getTitulo(),
                            "modalidad", matricula.getCurso().getModalidad(),
                            "fechaAcceso", fechaAcceso,
                            "origen", origen)));
            n.setEstadoEnvio("ENVIADO");
            n.setEnviadoEn(clock.instant());
            n.setUltimoError(null);
        } catch (MailDeliveryException exception) {
            n.setEstadoEnvio("ERROR");
            n.setUltimoError(exception.getMessage());
        }
        notificaciones.save(n);
        return n.getEstadoEnvio();
    }

    private void registrarCambio(Matricula m, String anterior, String nuevo, String motivo, Long actor) {
        HistorialEstadoMatricula h = new HistorialEstadoMatricula();
        h.setMatricula(m);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(actor);
        h.setRealizadoEn(clock.instant());
        historial.save(h);
    }

    private Map<Long, String> origenesReporte(List<ReporteMatriculaRespuesta> filas) {
        if (filas.isEmpty()) return Map.of();
        List<Long> ids = filas.stream().map(ReporteMatriculaRespuesta::matriculaId).toList();
        Map<Long, List<Pago>> pagosPorMatricula = pagos.findByMatricula_IdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getMatricula().getId()));
        return pagosPorMatricula.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> origenReporte(entry.getValue())));
    }

    private String origenReporte(List<Pago> pagosDeMatricula) {
        if (pagosDeMatricula.stream().anyMatch(p -> "EXONERADO".equals(p.getOrigen()))) return "EXONERADO";
        if (pagosDeMatricula.stream().anyMatch(p -> "MANUAL".equals(p.getOrigen()))) return "REGISTRADO_MANUAL";
        if (pagosDeMatricula.stream().anyMatch(p -> "APROBADO".equals(p.getEstado()))) return "PAGO_EN_LINEA";
        return pagosDeMatricula.getFirst().getOrigen();
    }

    private ReporteMatriculaRespuesta conOrigen(ReporteMatriculaRespuesta f, String origen) {
        return new ReporteMatriculaRespuesta(f.matriculaId(), f.alumno(), f.correo(), f.curso(), f.modalidad(),
                f.fechaMatricula(), f.fechaActivacion(), f.estadoMatricula(), origen,
                f.situacionAcademica(), f.estadoCertificado());
    }

    private boolean tieneRol(Long usuarioId, String codigo) {
        return adminUsuarios.obtener(usuarioId).roles().stream().anyMatch(rol -> codigo.equals(rol.name()));
    }

    private Curso curso(Long id) {
        return cursos.findWithDetalleById(id).orElseThrow(() -> new ResourceNotFoundException("El curso no existe."));
    }

    private String nombreUsuario(Long usuarioId) {
        if (usuarioId == null) return "Autoservicio / sistema";
        return usuarios.findWithPersonaById(usuarioId).map(u -> u.getPersona().nombreCompleto()).orElse("Usuario " + usuarioId);
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) throw new ForbiddenException();
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase();
    }

    private static String normalizarEstado(String valor) {
        return valor == null ? "" : valor.strip().toUpperCase();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial.");
        }
    }

    private static Instant inicio(LocalDate fecha) {
        // Se envía siempre un Instant tipado: PostgreSQL no puede inferir el tipo
        // de un parámetro nulo usado en una comparación opcional.
        return fecha == null
                ? LocalDate.of(1, 1, 1).atStartOfDay(LIMA).toInstant()
                : fecha.atStartOfDay(LIMA).toInstant();
    }

    private static Instant finExclusivo(LocalDate fecha) {
        return fecha == null
                ? LocalDate.of(9999, 12, 31).plusDays(1).atStartOfDay(LIMA).toInstant()
                : fecha.plusDays(1).atStartOfDay(LIMA).toInstant();
    }
}
