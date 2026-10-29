package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.utp.escuela.app.dto.CertificacionResponse;
import pe.edu.utp.escuela.app.dto.CertificacionResponse.CertificadoEmitido;
import pe.edu.utp.escuela.app.dto.CertificacionResponse.DatosCertificado;
import pe.edu.utp.escuela.app.dto.ConfirmarDatosRequest;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.entity.CertificadoFirmante;
import pe.edu.utp.escuela.app.entity.ConfiguracionInstitucional;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.CursoFirmante;
import pe.edu.utp.escuela.app.entity.LogroCertificacion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.event.ReevaluarCertificacionEvent;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.export.CertificadoPdfExport;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.CertificadoFirmanteRepository;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.ConfiguracionInstitucionalRepository;
import pe.edu.utp.escuela.app.repository.CursoFirmanteRepository;
import pe.edu.utp.escuela.app.repository.LogroCertificacionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.repository.PersonaRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;

/**
 * HU-032 — Obtener el certificado. Centraliza el cumplimiento de las condiciones ACTIVAS del curso
 * ({@link AvanceAcademicoService}), la finalización, la fecha de emisión, la confirmación de datos y
 * la emisión del documento.
 *
 * <p>Estados que ve el alumno antes de emitir: PENDIENTE_REQUISITOS (falta una condición, incluido un
 * examen con respuesta abierta sin calificar), PROGRAMADO (cumplió pero espera su fecha), PENDIENTE_DATOS
 * (llegó la fecha y falta confirmar nombres) y LISTO_PARA_EMITIR (espera cero: el alumno decide generar
 * o seguir mejorando). Después de emitir solo existe VIGENTE o ANULADO.
 *
 * <p>Fechas, en {@code America/Lima}: {@code fecha_base = max(finalización, fecha_fin del curso)} y la
 * emisión programada es esa fecha más los días de espera, a las 00:00. Con espera mayor que cero el
 * certificado se emite solo ese día si los datos están confirmados; con espera cero lo emite el alumno
 * cuando lo decide. Emitir congela nota, nivel y datos y cierra los intentos calificados (los de
 * práctica siguen); la finalización ya registrada se conserva aunque la matrícula venza o se cancele.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificacionService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));

    private final MatriculaRepository matriculas;
    private final LogroCertificacionRepository logros;
    private final CertificadoRepository certificados;
    private final CertificadoFirmanteRepository certificadoFirmantes;
    private final CursoFirmanteRepository cursoFirmantes;
    private final ConfiguracionInstitucionalRepository configuracion;
    private final NotificacionRepository notificaciones;
    private final PersonaRepository personas;
    private final MailService mailService;
    private final AvanceAcademicoService avanceAcademico;
    private final ArchivoAlmacenamientoService archivos;
    private final CertificadoPdfExport pdf;
    private final ImagenCertificadoService imagenes;
    private final CurrentUserService actual;
    private final PlatformTransactionManager transacciones;
    private final Clock clock;

    @Value("${application.frontend-base-url}")
    private String frontendBaseUrl;

    private final SecureRandom random = new SecureRandom();

    /** Resultado del cálculo de situación; {@code emitirYa} indica que corresponde emitir sin pedirle nada al alumno. */
    private record Situacion(Matricula matricula, AvanceAcademico avance, LogroCertificacion logro, Certificado certificado,
            String estado, String mensaje, boolean emitirYa, LocalDate fechaBase, int espera, boolean condicionesActivas,
            BigDecimal nota, String nivel) {
    }

    // =========================================================== API del alumno ==

    @Transactional
    public CertificacionResponse consultar(Long cursoId) {
        Matricula matricula = matriculaDelAlumno(cursoId);
        Situacion s = evaluar(matricula);
        if (s.emitirYa()) {
            emitir(s, "AUTOMATICA");
            s = evaluar(matricula);
        }
        return respuesta(s);
    }

    /** Guarda y confirma los nombres y apellidos a imprimir. Se puede repetir mientras no se haya emitido. */
    @Transactional
    public CertificacionResponse confirmarDatos(Long cursoId, ConfirmarDatosRequest peticion) {
        Matricula matricula = matriculaDelAlumno(cursoId);
        LogroCertificacion logro = logroDe(matricula);
        if (certificados.findByLogro_Matricula_Id(matricula.getId()).isPresent()) {
            throw new BusinessValidationException("Tu certificado ya fue emitido: sus datos no se pueden cambiar.");
        }
        Persona persona = matricula.getUsuario().getPersona();
        persona.setNombres(limpiar(peticion.nombres()));
        persona.setApellidoPaterno(limpiar(peticion.apellidoPaterno()));
        persona.setApellidoMaterno(limpiar(peticion.apellidoMaterno()));
        personas.save(persona);

        logro.setNombresConfirmados(persona.nombreCompleto());
        logro.setDatosConfirmadosEn(clock.instant());
        logro.setModificadoEn(clock.instant());
        logros.save(logro);

        Situacion s = evaluar(matricula);
        // Si la fecha programada ya pasó, la emisión continúa en cuanto se confirman los datos.
        if (s.emitirYa()) {
            emitir(s, "AUTOMATICA");
            s = evaluar(matricula);
        }
        return respuesta(s);
    }

    /** "Generar certificado ahora": solo con espera cero y todo en regla. Congela nota, nivel y datos. */
    @Transactional
    public CertificacionResponse emitirAhora(Long cursoId) {
        Matricula matricula = matriculaDelAlumno(cursoId);
        Situacion s = evaluar(matricula);
        if (s.certificado() != null) {
            throw new BusinessValidationException("Tu certificado ya fue emitido.");
        }
        if (!"LISTO_PARA_EMITIR".equals(s.estado())) {
            throw new BusinessValidationException(s.mensaje());
        }
        emitir(s, "SOLICITADA");
        return respuesta(evaluar(matricula));
    }

    /** Entrega el PDF a su dueño (o a un administrador). El PDF nunca viaja por correo. */
    @Transactional(readOnly = true)
    public Descarga descargar(String codigo) {
        Certificado certificado = certificados.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("El certificado no existe."));
        Long duenio = certificado.getLogro().getMatricula().getUsuario().getId();
        boolean esAdministrador = actual.get().hasRole("ADMINISTRADOR");
        if (!esAdministrador && !duenio.equals(actual.get().userId())) {
            throw new ResourceNotFoundException("El certificado no existe.");
        }
        if (Certificado.ANULADO.equals(certificado.getEstado())) {
            throw new BusinessValidationException("Este certificado fue anulado y ya no se puede descargar.");
        }
        return new Descarga(archivos.cargar(certificado.getArchivoPdfUrl()), "certificado-" + certificado.getCodigo() + ".pdf");
    }

    public record Descarga(Resource archivo, String nombre) {
    }

    // ============================================================ reevaluación ==

    /** Un cambio en el avance (lección, examen, asistencia) reevalúa la certificación, sin afectar a quien lo originó. */
    @TransactionalEventListener(fallbackExecution = true)
    public void alCambiarElAvance(ReevaluarCertificacionEvent evento) {
        reevaluarSeguro(evento.matriculaId());
    }

    /** Revisa los logros que dependen del tiempo (espera de emisión, datos pendientes). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    public void procesarPendientes() {
        for (LogroCertificacion logro : logros.findByEstadoIn(List.of(LogroCertificacion.ESPERA, LogroCertificacion.DATOS_PENDIENTES))) {
            reevaluarSeguro(logro.getMatricula().getId());
        }
    }

    private void reevaluarSeguro(Long matriculaId) {
        try {
            new TransactionTemplate(transacciones, definicionNueva()).executeWithoutResult(estado -> {
                Matricula matricula = matriculas.findById(matriculaId).orElse(null);
                if (matricula == null) {
                    return;
                }
                Situacion s = evaluar(matricula);
                if (s.emitirYa()) {
                    emitir(s, "AUTOMATICA");
                } else if ("PENDIENTE_DATOS".equals(s.estado())) {
                    pedirDatos(matricula);
                }
            });
        } catch (RuntimeException e) {
            log.warn("No se pudo reevaluar la certificación de la matrícula {}", matriculaId, e);
        }
    }

    private org.springframework.transaction.support.DefaultTransactionDefinition definicionNueva() {
        var definicion = new org.springframework.transaction.support.DefaultTransactionDefinition();
        definicion.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        return definicion;
    }

    // ============================================================== situación ==

    /** Calcula (y deja registrada en el logro) la situación del certificado de una matrícula. */
    private Situacion evaluar(Matricula matricula) {
        Curso curso = matricula.getCurso();
        LogroCertificacion logro = logroDe(matricula);
        AvanceAcademico avance = avanceAcademico.calcular(matricula);
        ReglaCurso regla = avance.ruta().regla();
        boolean virtual = "VIRTUAL".equals(curso.getModalidad());
        boolean condicionesActivas = regla.isRequiereProgreso() || regla.isRequiereExamenes()
                || (regla.isRequiereAsistencia() && !virtual);
        BigDecimal nota = avance.notaFinal();
        String nivel = nivelPara(regla, curso, nota);

        Certificado certificado = certificados.findByLogro_Matricula_Id(matricula.getId()).orElse(null);
        if (certificado != null) {
            String estado = Certificado.ANULADO.equals(certificado.getEstado()) ? "ANULADO" : "VIGENTE";
            return new Situacion(matricula, avance, logro, certificado, estado,
                    "ANULADO".equals(estado) ? "Este certificado fue anulado." : "Tu certificado fue emitido.",
                    false, null, regla.getDiasEsperaCertificado(), condicionesActivas, certificado.getNotaImpresa(),
                    certificado.getNivel());
        }

        Instant ahora = clock.instant();
        // La finalización se registra una vez y se conserva: vencer o cancelar después no la deshace.
        if (logro.getFechaFinalizacion() == null && condicionesActivas && avance.cumpleTodo()) {
            logro.setFechaFinalizacion(ahora);
            matricula.setFechaFinalizacion(ahora);
            matriculas.save(matricula);
        }
        boolean finalizado = logro.getFechaFinalizacion() != null;
        int espera = regla.getDiasEsperaCertificado();

        LocalDate fechaBase = null;
        Instant programada = null;
        String estado;
        String mensaje;
        boolean emitirYa = false;
        if (!finalizado) {
            estado = "PENDIENTE_REQUISITOS";
            mensaje = condicionesActivas ? avance.frase()
                    : "Este curso no tiene requisitos automáticos: la emisión del certificado la gestiona administración.";
        } else {
            fechaBase = logro.getFechaFinalizacion().atZone(LIMA).toLocalDate();
            if (curso.getFechaFin() != null && curso.getFechaFin().isAfter(fechaBase)) {
                fechaBase = curso.getFechaFin();
            }
            programada = fechaBase.plusDays(espera).atStartOfDay(LIMA).toInstant();
            boolean datos = logro.getDatosConfirmadosEn() != null;
            if (ahora.isBefore(programada)) {
                estado = "PROGRAMADO";
                mensaje = espera > 0
                        ? "Cumpliste los requisitos. Tu certificado se emitirá automáticamente el "
                                + FECHA_LARGA.format(programada.atZone(LIMA)) + ". Hasta entonces puedes mejorar tu nota con los intentos disponibles."
                        : "Cumpliste los requisitos. Podrás generar tu certificado desde el "
                                + FECHA_LARGA.format(programada.atZone(LIMA)) + ".";
            } else if (!datos) {
                estado = "PENDIENTE_DATOS";
                mensaje = "Confirma tus nombres y apellidos tal como quieres que aparezcan en tu certificado.";
            } else if (espera > 0) {
                // Llegó la fecha con los datos confirmados: se emite sola.
                estado = "PROGRAMADO";
                mensaje = "Tu certificado se está emitiendo.";
                emitirYa = true;
            } else {
                estado = "LISTO_PARA_EMITIR";
                mensaje = "Cumpliste todos los requisitos. Genera tu certificado ahora o sigue mejorando tu nota: "
                        + "al generarlo, tu nota y nivel quedan definitivos y se cierran los nuevos intentos calificados.";
            }
        }

        logro.setEstado(switch (estado) {
            case "PROGRAMADO" -> LogroCertificacion.ESPERA;
            case "PENDIENTE_DATOS" -> LogroCertificacion.DATOS_PENDIENTES;
            case "LISTO_PARA_EMITIR" -> LogroCertificacion.LISTO;
            default -> LogroCertificacion.EN_PROGRESO;
        });
        logro.setFechaEmisionProgramada(programada);
        logro.setModificadoEn(ahora);
        logros.save(logro);
        return new Situacion(matricula, avance, logro, null, estado, mensaje, emitirYa, fechaBase, espera,
                condicionesActivas, nota, nivel);
    }

    /** Normal desde la nota mínima; Refrendado desde el umbral configurado y solo si el curso tiene entidad. */
    private static String nivelPara(ReglaCurso regla, Curso curso, BigDecimal nota) {
        boolean refrendado = regla.isRequiereExamenes() && nota != null && curso.getEntidadCertificadora() != null
                && nota.compareTo(regla.getNotaRefrendado()) >= 0;
        return refrendado ? "REFRENDADO" : "NORMAL";
    }

    // ================================================================= emisión ==

    /**
     * HU-035 — Emite un certificado excepcional que {@code CertificadoAdminService} ya validó (motivo, datos
     * confirmados, ausencia de certificado y de revisiones pendientes). Nunca elige el nivel: lo decide la
     * nota definitiva con los umbrales del curso; sin nota, el nivel es Normal y no se imprime ninguna.
     */
    Certificado emitirExcepcional(Matricula matricula, String motivo, Long administradorId, Instant fechaFinalizacion) {
        Situacion base = evaluar(matricula);
        LogroCertificacion logro = base.logro();
        if (logro.getFechaFinalizacion() == null) {
            logro.setFechaFinalizacion(fechaFinalizacion);
            matricula.setFechaFinalizacion(fechaFinalizacion);
            matriculas.save(matricula);
            logros.save(logro);
        }
        Situacion s = new Situacion(matricula, base.avance(), logro, null, "LISTO_PARA_EMITIR",
                "Emisión manual administrativa.", false, null, base.espera(), base.condicionesActivas(), base.nota(),
                base.nivel());
        return emitir(s, "MANUAL", motivo, administradorId);
    }

    private void emitir(Situacion s, String tipoEmision) {
        emitir(s, tipoEmision, null, null);
    }

    private Certificado emitir(Situacion s, String tipoEmision, String motivoManual, Long administradorId) {
        Matricula matricula = s.matricula();
        Curso curso = matricula.getCurso();
        LogroCertificacion logro = s.logro();
        Instant ahora = clock.instant();
        boolean virtual = "VIRTUAL".equals(curso.getModalidad());
        LocalDate hoy = ahora.atZone(LIMA).toLocalDate();

        Certificado c = new Certificado();
        c.setLogro(logro);
        c.setCodigo(nuevoCodigo(hoy.getYear()));
        c.setEstado(Certificado.VIGENTE);
        c.setTipoEmision(tipoEmision);
        c.setNivel(s.nivel());
        c.setNombresImpresos(logro.getNombresConfirmados());
        c.setTituloCursoImpreso(curso.getTitulo());
        c.setTipoCursoImpreso(curso.getTipoCurso() != null ? curso.getTipoCurso().getNombre() : "Curso");
        c.setHorasImpresas(curso.getHorasAcademicas() != null ? curso.getHorasAcademicas() : BigDecimal.ZERO);
        // En vivo / híbrido: fechas del curso. Virtual sin fin: matrícula y finalización del alumno.
        LocalDate inicio = !virtual && curso.getFechaInicio() != null ? curso.getFechaInicio()
                : matricula.getFechaMatricula().atZone(LIMA).toLocalDate();
        LocalDate fin = !virtual && curso.getFechaFin() != null ? curso.getFechaFin()
                : logro.getFechaFinalizacion().atZone(LIMA).toLocalDate();
        c.setFechaInicioImpresa(inicio);
        c.setFechaFinImpresa(fin);
        c.setLugarEmision(configuracion.findByCodigo("LUGAR_EMISION_CERTIFICADO")
                .map(ConfiguracionInstitucional::getValor).orElse("Lima, Perú"));
        c.setEntidadImpresa("REFRENDADO".equals(s.nivel()) && curso.getEntidadCertificadora() != null
                ? curso.getEntidadCertificadora().getNombre() : null);
        c.setNotaImpresa(s.nota());
        c.setArchivoPdfUrl("/uploads/certificados/" + c.getCodigo() + ".pdf");
        c.setEmitidoEn(ahora);
        if ("SOLICITADA".equals(tipoEmision)) {
            c.setEmitidoPorUsuarioId(actual.get().userId());
        } else if ("MANUAL".equals(tipoEmision)) {
            c.setMotivoEmisionManual(motivoManual);
            c.setEmitidoPorUsuarioId(administradorId);
        }
        certificados.save(c);

        List<CertificadoFirmante> firmantes = new java.util.ArrayList<>();
        for (CursoFirmante cf : cursoFirmantes.findByCurso_IdOrderByOrdenAsc(curso.getId())) {
            CertificadoFirmante f = new CertificadoFirmante();
            f.setCertificado(c);
            f.setFirmante(cf.getFirmante());
            f.setOrden(cf.getOrden());
            f.setNombreImpreso(cf.getFirmante().getPersona().nombreCompleto());
            f.setCargoImpreso(cf.getFirmante().getCargoFirma());
            f.setImagenFirmaUrl(cf.getFirmante().getImagenFirmaUrl());
            firmantes.add(f);
        }
        certificadoFirmantes.saveAll(firmantes);

        String verificacion = frontendBaseUrl + "/verificar-certificado?codigo=" + c.getCodigo();
        String logoEntidad = curso.getEntidadCertificadora() != null ? curso.getEntidadCertificadora().getLogoUrl() : null;
        archivos.guardarBytes("certificados", c.getCodigo() + ".pdf",
                pdf.generar(c, firmantes, verificacion, imagenes::cargar, logoEntidad));

        logro.setEstado(LogroCertificacion.EMITIDO);
        logro.setNotaFinal(s.nota());
        logro.setProgresoFinal(BigDecimal.valueOf(s.avance().porcentaje()));
        logro.setAsistenciaFinal(s.avance().asistencia() == null ? null : BigDecimal.valueOf(s.avance().asistencia()));
        logro.setNivel(s.nivel());
        logro.setModificadoEn(ahora);
        logros.save(logro);

        avisarCertificado(matricula, c);
        return c;
    }

    private String nuevoCodigo(int anio) {
        for (int intento = 0; intento < 10; intento++) {
            StringBuilder sufijo = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sufijo.append(ALFABETO_CODIGO.charAt(random.nextInt(ALFABETO_CODIGO.length())));
            }
            String codigo = "ESEJUR-" + anio + "-" + sufijo;
            if (!certificados.existsByCodigo(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No pudimos generar un código único para el certificado.");
    }

    // ================================================================== correos ==

    /** Un correo, una sola vez, cuando ya cumple todo y solo faltan sus datos. */
    private void pedirDatos(Matricula matricula) {
        String tipo = "CERT_DATOS_" + matricula.getId();
        if (notificaciones.existsByUsuario_IdAndTipo(matricula.getUsuario().getId(), tipo)) {
            return;
        }
        enviar(matricula, tipo, "Confirma tus datos para emitir tu certificado", "mail/certificado-datos.html",
                Map.of("alumno", matricula.getUsuario().getPersona().getNombres(), "curso", matricula.getCurso().getTitulo(),
                        "enlace", frontendBaseUrl + "/aula/" + matricula.getCurso().getId()));
    }

    private void avisarCertificado(Matricula matricula, Certificado certificado) {
        enviar(matricula, "CERT_LISTO_" + certificado.getId(), "Tu certificado está listo", "mail/certificado-listo.html",
                Map.of("alumno", matricula.getUsuario().getPersona().getNombres(), "curso", matricula.getCurso().getTitulo(),
                        "codigo", certificado.getCodigo(),
                        "enlace", frontendBaseUrl + "/aula/" + matricula.getCurso().getId()));
    }

    /** El fallo del correo queda registrado; nunca elimina el certificado ni reabre nada. */
    private void enviar(Matricula matricula, String tipo, String asunto, String plantilla, Map<String, String> campos) {
        Notificacion n = new Notificacion();
        n.setUsuario(matricula.getUsuario());
        n.setTipo(tipo);
        n.setDestinatario(matricula.getUsuario().getCorreo());
        n.setAsunto(asunto);
        try {
            mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), asunto, plantilla, campos));
            n.setEstadoEnvio("ENVIADO");
            n.setEnviadoEn(clock.instant());
        } catch (MailDeliveryException e) {
            n.setEstadoEnvio("ERROR");
            n.setUltimoError(e.getMessage());
        }
        notificaciones.save(n);
    }

    // ================================================================ respuesta ==

    private CertificacionResponse respuesta(Situacion s) {
        Matricula m = s.matricula();
        Curso curso = m.getCurso();
        Persona p = m.getUsuario().getPersona();
        LogroCertificacion logro = s.logro();
        boolean emitido = s.certificado() != null;

        CertificadoEmitido emitidoDto = null;
        if (emitido) {
            Certificado c = s.certificado();
            emitidoDto = new CertificadoEmitido(c.getCodigo(), c.getEstado(), c.getNivel(), c.getNotaImpresa(),
                    c.getEmitidoEn(), c.getNombresImpresos(), c.getEntidadImpresa(),
                    "/api/app/certificados/" + c.getCodigo() + "/descargar");
        }
        boolean finalizado = logro.getFechaFinalizacion() != null;
        boolean fechaLlegada = finalizado && logro.getFechaEmisionProgramada() != null
                && !clock.instant().isBefore(logro.getFechaEmisionProgramada());
        String entidad = "REFRENDADO".equals(s.nivel()) && curso.getEntidadCertificadora() != null
                ? curso.getEntidadCertificadora().getNombre() : null;

        return new CertificacionResponse(curso.getId(), curso.getTitulo(), s.estado(), s.mensaje(),
                s.avance().condiciones(), s.avance().frase(), s.avance().ruta().regla().isRequiereExamenes(),
                s.nota(), s.nivel(), entidad, logro.getFechaFinalizacion(), s.fechaBase(),
                logro.getFechaEmisionProgramada(), s.espera(), s.espera() > 0,
                new DatosCertificado(p.getNombres(), p.getApellidoPaterno(), p.getApellidoMaterno(),
                        logro.getDatosConfirmadosEn() != null, logro.getDatosConfirmadosEn()),
                // Puede confirmar/corregir sus datos desde que finaliza y mientras no haya emitido.
                finalizado && !emitido,
                !emitido && fechaLlegada && "LISTO_PARA_EMITIR".equals(s.estado()),
                emitidoDto);
    }

    // =================================================================== helpers ==

    private Matricula matriculaDelAlumno(Long cursoId) {
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
        return matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));
    }

    private LogroCertificacion logroDe(Matricula matricula) {
        return logros.findByMatricula_Id(matricula.getId()).orElseGet(() -> {
            LogroCertificacion nuevo = new LogroCertificacion();
            nuevo.setMatricula(matricula);
            nuevo.setEstado(LogroCertificacion.EN_PROGRESO);
            nuevo.setModificadoEn(clock.instant());
            return logros.save(nuevo);
        });
    }

    private static String limpiar(String valor) {
        return valor.strip().replaceAll("\\s+", " ");
    }
}
